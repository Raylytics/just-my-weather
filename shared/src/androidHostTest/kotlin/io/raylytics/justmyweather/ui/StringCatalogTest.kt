package io.raylytics.justmyweather.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The shared string catalog is read by Compose Multiplatform, not by Android's
 * resource compiler, and the two disagree on escapes: Android wants `\'`,
 * Compose shows the backslash. (It shipped to the emulator once, as
 * "Couldn\'t find what you\'re looking for?" — Maestro caught it.) This holds
 * the catalog to what Compose reads correctly.
 */
class StringCatalogTest {
    private val strings: Map<String, String> =
        Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""")
            .findAll(File("src/commonMain/composeResources/values/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `the catalog is there`() {
        assertTrue(strings.size > 100, "only ${strings.size} strings — wrong file?")
    }

    @Test
    fun `no Android-style escapes`() {
        val escaped = strings.filterValues { '\\' in it }
        assertEquals(emptyMap<String, String>(), escaped, "write ' and \" plainly; Compose would show the backslash")
    }

    @Test
    fun `placeholders are numbered from one without gaps`() {
        for ((key, text) in strings) {
            val numbers = Regex("""%(\d+)\${'$'}s""").findAll(text).map { it.groupValues[1].toInt() }.toSet()
            assertEquals((1..numbers.size).toSet(), numbers, key)
            assertTrue(!Regex("""%[sd]""").containsMatchIn(text), "$key: use %1\$s, not a bare %s")
        }
    }
}
