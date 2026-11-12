package com.cylldby.blocker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure helpers only — the SharedPreferences paths need an Android context. */
class UnlockManagerTest {

    @Test
    fun generatedPaperPasswordHasThirtyUnambiguousChars() {
        repeat(20) {
            val raw = UnlockManager.generateMasterRaw()
            assertThat(raw).hasLength(UnlockManager.MASTER_LENGTH)
            assertThat(raw.none { it in "01IO" }).isTrue()
            assertThat(raw.all { it.isUpperCase() || it.isDigit() }).isTrue()
        }
    }

    @Test
    fun displayGroupsIntoFiveCharacterChunks() {
        val raw = "ABCDEFGHJKL23456789PQRSTUVWXYZ"
        assertThat(raw).hasLength(UnlockManager.MASTER_LENGTH)
        assertThat(UnlockManager.displayMaster(raw))
            .isEqualTo("ABCDE-FGHJK-L2345-6789P-QRSTU-VWXYZ")
    }

    @Test
    fun normalizeMasterAcceptsHandwrittenGrouping() {
        val raw = "ABCDEFGHJKL23456789PQRSTUVWXYZ"
        assertThat(UnlockManager.normalizeMaster("abcde-fghjk l2345 6789p-qrstu vwxyz"))
            .isEqualTo(raw.uppercase())
    }

    @Test
    fun emergencyShapeRequiresExactlySixDigits() {
        assertThat(UnlockManager.isEmergencyShape("123456")).isTrue()
        assertThat(UnlockManager.isEmergencyShape("000000")).isTrue()
        assertThat(UnlockManager.isEmergencyShape("12345")).isFalse()
        assertThat(UnlockManager.isEmergencyShape("1234567")).isFalse()
        assertThat(UnlockManager.isEmergencyShape("12a456")).isFalse()
        assertThat(UnlockManager.isEmergencyShape("")).isFalse()
    }

    @Test
    fun unlockOptionsIncludeAnUntilMorningChoice() {
        assertThat(UnlockManager.UNLOCK_OPTIONS.size).isGreaterThan(0)
        assertThat(UnlockManager.UNLOCK_OPTIONS.contains(-1)).isTrue()
        assertThat(UnlockManager.DEFAULT_UNLOCK_MINUTES).isEqualTo(30)
    }
}
