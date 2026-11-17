package com.example.myproducts.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Enforces the client's layering with a failing build, like the backend's architecture
 * test. Kotlin has no package-private visibility, so `internal` alone cannot stop a screen
 * from importing a DTO; this test can.
 */
class ArchitectureTest {
    // Unit tests run with the app module as the working directory.
    private val root = File("src/main/java/com/example/myproducts")

    private fun kotlinFiles(dir: String): List<File> =
        File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun offenders(files: List<File>, forbidden: Regex): List<String> =
        files.filter { file -> file.readLines().any { it.startsWith("import ") && forbidden.containsMatchIn(it) } }
            .map { it.relativeTo(root).path }

    @Test
    fun `the source tree is where this test expects it (guards against a vacuous pass)`() {
        assertEquals(true, kotlinFiles("data").isNotEmpty() && kotlinFiles("domain").isNotEmpty())
    }

    @Test
    fun `DTOs and the Retrofit API are only imported by data and di`() {
        val outside = kotlinFiles("").filterNot {
            val path = it.relativeTo(root).path
            path.startsWith("data/") || path.startsWith("di/")
        }

        assertEquals(emptyList<String>(), offenders(outside, Regex("""\.data\.remote\.""")))
    }

    @Test
    fun `ui never imports the data layer or Retrofit - ViewModels depend on the repository interface`() {
        assertEquals(emptyList<String>(), offenders(kotlinFiles("ui"), Regex("""\.data\.|retrofit2|okhttp3""")))
    }

    @Test
    fun `domain is pure Kotlin - no Android, data, ui, Retrofit or JSON imports`() {
        assertEquals(
            emptyList<String>(),
            offenders(kotlinFiles("domain"), Regex("""android|\.data\.|\.ui\.|retrofit2|okhttp3|kotlinx\.serialization""")),
        )
    }

    @Test
    fun `no literal colours or raw dp outside ui-theme - restyling means editing the theme only`() {
        val screens = kotlinFiles("ui").filterNot { it.relativeTo(root).path.startsWith("ui/theme/") } +
            listOf(File(root, "MainActivity.kt"))
        val literal = Regex("""Color\(0x|\b\d+(\.\d+)?\.dp\b""")

        val found = screens.filter { file -> file.readLines().any { literal.containsMatchIn(it) } }.map { it.relativeTo(root).path }

        assertEquals(emptyList<String>(), found)
    }
}
