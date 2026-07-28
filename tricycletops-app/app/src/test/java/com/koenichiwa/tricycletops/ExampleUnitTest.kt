package com.koenichiwa.tricycletops

import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class  ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun verifyBleServiceUuidFormat() {
        // The peripheral has GATT service UUID 'fff0'.
        // In the standard 128-bit BLE UUID format: 0000xxxx-0000-1000-8000-00805f9b34fb
        val targetUuidString = "0000fff0-0000-1000-8000-00805f9b34fb"
        val parsedUuid = UUID.fromString(targetUuidString)

        // Ensure standard string representation matches
        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", parsedUuid.toString())

        // Check that 16-bit short part matches our expectations
        val mostSigBits = parsedUuid.mostSignificantBits
        val shortUuid = (mostSigBits ushr 32) and 0xFFFFL
        assertEquals(0xfff0L, shortUuid)
    }

    @Test
    fun verifyPeripheralNameMatching() {
        val peripheralName = "Tricycletops"
        val matchesName = peripheralName.contains("Tricycletops", ignoreCase = true)
        assertTrue(matchesName)

        val lowercaseName = "tricycletops"
        assertTrue(lowercaseName.contains("Tricycletops", ignoreCase = true))

        val unrelatedName = "MySmartWatch"
        assertFalse(unrelatedName.contains("Tricycletops", ignoreCase = true))
    }
}
