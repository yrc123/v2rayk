import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficFormatTest {

    @Test
    fun zeroBytes_showsBytesWithoutDecimal() {
        assertEquals("0 B", formatTraffic(0))
    }

    @Test
    fun underOneKilobyte_showsBytesWithoutDecimal() {
        assertEquals("512 B", formatTraffic(512))
    }

    @Test
    fun exactlyOneKilobyte_promotesToKB() {
        assertEquals("1.0 KB", formatTraffic(1024))
    }

    @Test
    fun kilobytes_oneDecimal() {
        assertEquals("1.5 KB", formatTraffic(1536))
    }

    @Test
    fun exactlyOneMegabyte() {
        assertEquals("1.0 MB", formatTraffic(1048576))
    }

    @Test
    fun exactlyOneGigabyte() {
        assertEquals("1.0 GB", formatTraffic(1073741824))
    }
}
