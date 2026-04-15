package net.notjustanna.patcher.manifest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Per-brand record of which upstream source files (by SHA-256) produced the
 * currently-published output variants.
 */
@Serializable
data class BrandManifest(
    /** Map of source filename (as extracted into working/input/icons/<brand>/) to "sha256:<hex>". */
    val sources: Map<String, String>,
)

/**
 * Top-level `packages/sha.json` manifest.
 *
 * On each patcher run, we read the prior manifest (if any) and skip the
 * expensive image processing pipeline for brands whose source SHAs AND
 * patcher version both still match.
 */
@Serializable
data class ShaManifest(
    val patcherVersion: String,
    val generatedAt: String,
    val upstreamPackages: Map<String, String>,
    val brands: Map<String, BrandManifest>,
) {
    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
        }

        fun loadOrNull(file: File): ShaManifest? {
            if (!file.exists()) return null
            return try {
                json.decodeFromString<ShaManifest>(file.readText())
            } catch (e: Exception) {
                System.err.println("⚠️  Failed to parse ${file.absolutePath}: ${e.message}")
                null
            }
        }

        fun save(manifest: ShaManifest, file: File) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(manifest))
        }

        /** Stream-hash a file to a "sha256:<hex>" string. */
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
