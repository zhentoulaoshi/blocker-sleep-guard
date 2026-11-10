package com.cylldby.blocker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsBlockerTest {

    // --- isDomainBlocked ---

    @Test
    fun `exact match blocks domain`() {
        assertThat(DnsBlocker.isDomainBlocked("reddit.com", setOf("reddit.com"))).isTrue()
    }

    @Test
    fun `subdomain of blocked domain is blocked`() {
        assertThat(DnsBlocker.isDomainBlocked("www.reddit.com", setOf("reddit.com"))).isTrue()
        assertThat(DnsBlocker.isDomainBlocked("old.reddit.com", setOf("reddit.com"))).isTrue()
    }

    @Test
    fun `unrelated domain is not blocked`() {
        assertThat(DnsBlocker.isDomainBlocked("google.com", setOf("reddit.com"))).isFalse()
    }

    @Test
    fun `partial suffix match does not block`() {
        // "notreddit.com" should NOT be blocked by rule "reddit.com"
        assertThat(DnsBlocker.isDomainBlocked("notreddit.com", setOf("reddit.com"))).isFalse()
    }

    @Test
    fun `blocking is case-insensitive`() {
        assertThat(DnsBlocker.isDomainBlocked("REDDIT.COM", setOf("reddit.com"))).isTrue()
    }

    @Test
    fun `empty blocklist blocks nothing`() {
        assertThat(DnsBlocker.isDomainBlocked("reddit.com", emptySet())).isFalse()
    }

    @Test
    fun `multiple rules in blocklist work independently`() {
        val rules = setOf("reddit.com", "twitter.com", "tiktok.com")
        assertThat(DnsBlocker.isDomainBlocked("m.twitter.com", rules)).isTrue()
        assertThat(DnsBlocker.isDomainBlocked("youtube.com", rules)).isFalse()
    }

    // --- parseDnsQueryDomain ---

    @Test
    fun `parses simple DNS query for reddit dot com`() {
        // Hand-crafted minimal DNS query for "reddit.com"
        val dns = buildDnsQuery("reddit", "com")
        assertThat(DnsBlocker.parseDnsQueryDomain(dns)).isEqualTo("reddit.com")
    }

    @Test
    fun `parses three-label domain`() {
        val dns = buildDnsQuery("www", "example", "org")
        assertThat(DnsBlocker.parseDnsQueryDomain(dns)).isEqualTo("www.example.org")
    }

    @Test
    fun `returns null for packet too short`() {
        assertThat(DnsBlocker.parseDnsQueryDomain(ByteArray(5))).isNull()
    }

    @Test
    fun `returns null for truncated label`() {
        // 12 header bytes + label len 10 but only 3 bytes of label data
        val dns = ByteArray(12 + 1 + 3) { 0 }
        dns[12] = 10  // label length 10, but only 3 bytes follow → truncated
        assertThat(DnsBlocker.parseDnsQueryDomain(dns)).isNull()
    }

    // --- buildNxdomainResponse ---

    @Test
    fun `NXDOMAIN response sets QR bit`() {
        val query = buildDnsQuery("reddit", "com")
        val response = DnsBlocker.buildNxdomainResponse(query)
        assertThat(response[2].toInt() and 0x80).isEqualTo(0x80)
    }

    @Test
    fun `NXDOMAIN response sets RCODE to 3`() {
        val query = buildDnsQuery("reddit", "com")
        val response = DnsBlocker.buildNxdomainResponse(query)
        assertThat(response[3].toInt() and 0x0F).isEqualTo(3)
    }

    @Test
    fun `NXDOMAIN response preserves transaction ID`() {
        val query = buildDnsQuery("reddit", "com")
        query[0] = 0xAB.toByte(); query[1] = 0xCD.toByte()
        val response = DnsBlocker.buildNxdomainResponse(query)
        assertThat(response[0]).isEqualTo(0xAB.toByte())
        assertThat(response[1]).isEqualTo(0xCD.toByte())
    }

    // --- normalizeInputDomain ---

    @Test
    fun `strips https prefix`() {
        assertThat(DnsBlocker.normalizeInputDomain("https://reddit.com")).isEqualTo("reddit.com")
    }

    @Test
    fun `strips www prefix`() {
        assertThat(DnsBlocker.normalizeInputDomain("www.reddit.com")).isEqualTo("reddit.com")
    }

    @Test
    fun `lowercases domain`() {
        assertThat(DnsBlocker.normalizeInputDomain("Reddit.COM")).isEqualTo("reddit.com")
    }

    @Test
    fun `plain domain is unchanged`() {
        assertThat(DnsBlocker.normalizeInputDomain("reddit.com")).isEqualTo("reddit.com")
    }

    // --- helpers ---

    private fun buildDnsQuery(vararg labels: String): ByteArray {
        // 12-byte header (all zeros = standard query) + QNAME + null terminator + QTYPE + QCLASS
        val qname = labels.flatMap { label ->
            listOf(label.length.toByte()) + label.map { it.code.toByte() }
        } + listOf(0.toByte())
        val header = ByteArray(12)
        val typeClass = byteArrayOf(0, 1, 0, 1)  // A record, IN class
        return header + qname.toByteArray() + typeClass
    }

    private operator fun ByteArray.plus(other: ByteArray) = this.copyOf(size + other.size).also {
        other.copyInto(it, size)
    }

    private fun List<Byte>.toByteArray() = ByteArray(size) { this[it] }
}
