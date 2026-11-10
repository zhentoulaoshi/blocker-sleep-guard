package com.cylldby.blocker.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cylldby.blocker.BuildConfig
import com.cylldby.blocker.BlockerApp
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.data.model.SiteUsage
import com.cylldby.blocker.util.DnsBlocker
import com.cylldby.blocker.util.NightGuardManager
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var alwaysBlockedDomains: Set<String> = emptySet()
    @Volatile private var quotaDomains: Map<String, Int> = emptyMap()

    private val domainMinuteStamps = mutableMapOf<String, MutableSet<Int>>()
    private val domainMinutesUsedToday = mutableMapOf<String, Int>()
    private var todayDateStr: String = todayDate()

    private val outputLock = Any()
    private val usageLock = Any()

    // Rate-limit blocked-site notifications: domain → last notification timestamp
    private val domainLastNotified = mutableMapOf<String, Long>()

    companion object {
        const val ACTION_START = "com.cylldby.blocker.VPN_START"
        const val ACTION_STOP  = "com.cylldby.blocker.VPN_STOP"
        private const val TAG  = "BlockerVpn"
        private const val NOTIFICATION_ID = 1

        private const val VPN_ADDRESS = "192.168.50.1"
        private const val VPN_DNS     = "192.168.50.5"

        // Resolve through DNS servers that are actually reachable from mainland
        // China. Hard-coding 8.8.8.8 made every allowed lookup time out here.
        private val UPSTREAM_DNS_LIST = listOf(
            InetAddress.getByName("223.5.5.5"),   // AliDNS
            InetAddress.getByName("119.29.29.29") // DNSPod
        )

        // Second line of defence at night: video / short-video / feed domains.
        // The accessibility service is the primary guard; this only helps when
        // the user has enabled the site-blocking VPN in the Sites tab.
        private val NIGHT_BLOCKED_SUFFIXES = setOf(
            "douyin.com", "iesdouyin.com", "snssdk.com", "amemv.com",
            "kuaishou.com", "kwai.net", "gifshow.com",
            "bilibili.com", "bilivideo.com", "hdslb.com",
            "ixigua.com", "toutiao.com", "toutiaovod.com",
            "weibo.com", "weibocdn.com", "xiaohongshu.com", "zhihu.com",
            "v.qq.com", "video.qq.com", "youku.com", "iqiyi.com",
            "mgtv.com", "pptv.com", "douyu.com", "huya.com"
        )

        private const val DNS_PORT = 53
        private const val MTU = 32767

        private fun todayDate() =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startVpn()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BlockerApp.CHANNEL_ID)
            .setContentTitle("Blocker active")
            .setContentText("Site blocking is running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startVpn() {
        vpnInterface = Builder()
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("192.168.50.0", 24)
            .addDnsServer(VPN_DNS)
            .setMtu(MTU)
            .setSession("BlockerVPN")
            .establish() ?: run {
                Log.e(TAG, "VPN establish() returned null")
                return
            }

        Log.d(TAG, "VPN established — interface=$VPN_ADDRESS  dns=$VPN_DNS")

        serviceScope.launch {
            (application as BlockerApp).repository.allSiteRules.collect { sites ->
                val always = mutableSetOf<String>()
                val quota = mutableMapOf<String, Int>()
                for (site in sites) {
                    val d = site.domain.lowercase().trimStart('.')
                    if (site.dailyLimitMinutes == 0) {
                        always.add(d)
                    } else {
                        quota[d] = site.dailyLimitMinutes
                    }
                }
                alwaysBlockedDomains = always
                quotaDomains = quota
                Log.d(TAG, "Domain list updated: ${always.size} always, ${quota.size} quota")

                // Load today's usage for quota domains
                loadTodayUsage(quota.keys)
            }
        }

        serviceScope.launch { runPacketLoop() }
    }

    private suspend fun loadTodayUsage(domains: Set<String>) {
        val date = todayDate()
        val repo = (application as BlockerApp).repository
        val loaded = mutableMapOf<String, Int>()
        for (domain in domains) {
            val usage = repo.getSiteUsage(domain, date)
            if (usage != null) loaded[domain] = usage.usedMinutes
        }
        synchronized(usageLock) {
            for ((d, mins) in loaded) {
                domainMinutesUsedToday[d] = mins
            }
        }
    }

    private fun stopVpn() {
        serviceScope.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun runPacketLoop() {
        val pfd = vpnInterface ?: return
        val input  = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf    = ByteArray(MTU)

        Log.i(TAG, "Packet loop started")
        while (serviceScope.isActive) {
            val len = try { input.read(buf) } catch (_: Exception) { break }
            if (len < 28) continue  // too small for IPv4 + UDP header + minimal DNS

            // Inline pre-filter: skip non-IPv4, non-UDP, non-DNS packets without
            // allocating or launching a coroutine — these are the vast majority.
            if ((buf[0].toInt() and 0xF0) ushr 4 != 4) continue  // not IPv4
            if (buf[9].toInt() and 0xFF != 17) continue           // not UDP
            val ihl = (buf[0].toInt() and 0x0F) * 4
            if (len < ihl + 8) continue
            val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
            if (dstPort != DNS_PORT) continue                      // not DNS

            val packet = buf.copyOf(len)
            serviceScope.launch { handlePacket(packet, output) }
        }
        Log.i(TAG, "Packet loop ended")
    }

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        val buf = ByteBuffer.wrap(packet)
        if (!isIPv4(buf)) return
        val ip = parseIPv4Header(buf) ?: return

        if (ip.protocol != 17) return
        if (ip.dstPort != DNS_PORT) return

        val domain = DnsBlocker.parseDnsQueryDomain(ip.payload) ?: run {
            Log.d(TAG, "Could not parse DNS query (likely a response packet, ignoring)")
            return
        }

        // Check date rollover
        val currentDate = todayDate()
        if (currentDate != todayDateStr) {
            synchronized(usageLock) {
                todayDateStr = currentDate
                domainMinuteStamps.clear()
                domainMinutesUsedToday.clear()
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "DNS query: $domain")

        // Track quota usage for quota domains
        val qDomains = quotaDomains
        val matchedQuota = qDomains.keys.find { k ->
            domain == k || domain.endsWith(".$k")
        }
        if (matchedQuota != null) {
            val currentMinute = (System.currentTimeMillis() / 60_000 % 1440).toInt()
            synchronized(usageLock) {
                val stamps = domainMinuteStamps.getOrPut(matchedQuota) { mutableSetOf() }
                if (stamps.add(currentMinute)) {
                    val newCount = (domainMinutesUsedToday[matchedQuota] ?: 0) + 1
                    domainMinutesUsedToday[matchedQuota] = newCount
                    val date = todayDateStr
                    serviceScope.launch {
                        try {
                            (application as BlockerApp).repository.upsertSiteUsage(
                                SiteUsage(matchedQuota, date, newCount)
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to persist site usage: ${e.message}")
                        }
                    }
                }
            }
        }

        if (isEffectivelyBlocked(domain)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "BLOCKING $domain")
            postBlockedNotification(domain)
            writeTun(output, buildUdpIpPacket(
                ip.dstIp, ip.srcIp, DNS_PORT, ip.srcPort,
                DnsBlocker.buildNxdomainResponse(ip.payload)
            ))
        } else {
            forwardDns(ip, output)
        }
    }

    private fun postBlockedNotification(domain: String) {
        val now = System.currentTimeMillis()
        synchronized(domainLastNotified) {
            if (now - (domainLastNotified[domain] ?: 0) < 30_000) return
            domainLastNotified[domain] = now
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, BlockerApp.CHANNEL_BLOCKED_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("Site blocked")
            .setContentText("$domain is blocked by Blocker")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(domain.hashCode(), notification)
    }

    private fun isEffectivelyBlocked(domain: String): Boolean {
        val always = alwaysBlockedDomains
        val quota = quotaDomains

        val alwaysMatch = DnsBlocker.isDomainBlocked(domain, always)
        if (alwaysMatch) return true

        if (NightGuardManager.isNightActive(this) &&
            DnsBlocker.isDomainBlocked(domain, NIGHT_BLOCKED_SUFFIXES)
        ) return true

        val matchedQuota = quota.keys.find { k ->
            domain == k || domain.endsWith(".$k")
        } ?: return false

        val usedMinutes = synchronized(usageLock) {
            domainMinutesUsedToday[matchedQuota] ?: 0
        }
        return usedMinutes >= (quota[matchedQuota] ?: Int.MAX_VALUE)
    }

    private fun forwardDns(ip: IpHeader, output: FileOutputStream) {
        for (server in UPSTREAM_DNS_LIST) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 3_000

                socket.send(DatagramPacket(ip.payload, ip.payload.size, server, DNS_PORT))

                val respBuf = ByteArray(512)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)

                writeTun(output, buildUdpIpPacket(
                    ip.dstIp, ip.srcIp, DNS_PORT, ip.srcPort,
                    respBuf.copyOf(respPacket.length)
                ))
                return
            } catch (e: Exception) {
                Log.w(TAG, "DNS via ${server.hostAddress} failed: ${e.message}")
            } finally {
                socket?.close()
            }
        }
        Log.w(TAG, "All upstream DNS servers failed — sending SERVFAIL")
        writeTun(output, buildUdpIpPacket(
            ip.dstIp, ip.srcIp, DNS_PORT, ip.srcPort,
            buildServfailResponse(ip.payload)
        ))
    }

    private fun writeTun(output: FileOutputStream, data: ByteArray) {
        synchronized(outputLock) {
            try { output.write(data) } catch (e: Exception) {
                Log.w(TAG, "TUN write error: ${e.message}")
            }
        }
    }

    private fun buildServfailResponse(query: ByteArray): ByteArray {
        val r = query.copyOf()
        r[2] = (r[2].toInt() or 0x80).toByte()
        r[3] = (r[3].toInt() or 0x02).toByte()
        return r
    }

    private data class IpHeader(
        val srcIp: ByteArray, val dstIp: ByteArray,
        val protocol: Int,
        val srcPort: Int, val dstPort: Int,
        val payload: ByteArray
    )

    private fun isIPv4(buf: ByteBuffer) =
        buf.limit() >= 1 && (buf.get(0).toInt() and 0xF0) shr 4 == 4

    private fun parseIPv4Header(buf: ByteBuffer): IpHeader? {
        if (buf.limit() < 20) return null
        val ihl = (buf.get(0).toInt() and 0x0F) * 4
        if (buf.limit() < ihl + 8) return null
        val protocol = buf.get(9).toInt() and 0xFF
        val srcIp = ByteArray(4).also { buf.position(12); buf.get(it) }
        val dstIp = ByteArray(4).also { buf.get(it) }
        buf.position(ihl)
        val srcPort = ((buf.get().toInt() and 0xFF) shl 8) or (buf.get().toInt() and 0xFF)
        val dstPort = ((buf.get().toInt() and 0xFF) shl 8) or (buf.get().toInt() and 0xFF)
        buf.position(ihl + 8)
        val payload = ByteArray(buf.remaining()).also { buf.get(it) }
        return IpHeader(srcIp, dstIp, protocol, srcPort, dstPort, payload)
    }

    private fun buildUdpIpPacket(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen   = 8 + payload.size
        val totalLen = 20 + udpLen
        val buf = ByteBuffer.allocate(totalLen)
        buf.put(0x45.toByte()); buf.put(0); buf.putShort(totalLen.toShort())
        buf.putInt(0)
        buf.put(64)
        buf.put(17)
        buf.putShort(0)
        buf.put(srcIp); buf.put(dstIp)
        buf.putShort(srcPort.toShort()); buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort()); buf.putShort(0)
        buf.put(payload)
        val bytes = buf.array()
        val cs = ipChecksum(bytes, 0, 20)
        bytes[10] = (cs shr 8).toByte(); bytes[11] = (cs and 0xFF).toByte()
        return bytes
    }

    private fun ipChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0; var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
        }
        if ((length and 1) != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
