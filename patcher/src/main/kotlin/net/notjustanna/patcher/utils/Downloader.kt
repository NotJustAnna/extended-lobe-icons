package net.notjustanna.patcher.utils

import net.notjustanna.patcher.config.Config
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream

/**
 * Download packages from npm registry and extract to input directory
 */
object Downloader {
    private const val NPM_REGISTRY = "https://registry.npmjs.org"
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun run() {
        println("📦 Downloading and caching packages...")

        if (!Config.INPUT_ICONS_DIR.exists()) {
            Config.INPUT_ICONS_DIR.mkdirs()
        }

        val packages = readPackageJson()
        if (packages.isEmpty()) {
            System.err.println("⚠️  No packages found in upstream/package.json")
            return
        }

        for ((pkgName, version) in packages) {
            try {
                downloadAndExtract(pkgName, version)
            } catch (e: Exception) {
                System.err.println("⚠️  Error downloading $pkgName: ${e.message}")
            }
        }

        println("✅ Packages processed")
    }

    /**
     * Read packages from upstream/package.json
     */
    private fun readPackageJson(): List<Pair<String, String>> {
        return try {
            if (!Config.UPSTREAM_PACKAGE_JSON.exists()) {
                System.err.println("⚠️  upstream/package.json not found at ${Config.UPSTREAM_PACKAGE_JSON.absolutePath}")
                return emptyList()
            }

            val packageJsonContent = Config.UPSTREAM_PACKAGE_JSON.readText()
            val packageJson = json.parseToJsonElement(packageJsonContent).jsonObject
            val dependencies = packageJson["dependencies"]?.jsonObject ?: return emptyList()

            dependencies.entries.mapNotNull { (pkgName, versionElement) ->
                val versionSpec = versionElement.jsonPrimitive.content
                val version = versionSpec.trimStart('^', '~', '>', '<', '=')
                if (pkgName.startsWith("@lobehub/icons-static-")) {
                    pkgName to version
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            System.err.println("⚠️  Error reading upstream/package.json: ${e.message}")
            emptyList()
        }
    }

    /**
     * Download and extract package from npm registry
     * Query API to get tarball URL, cache it, then extract
     */
    private fun downloadAndExtract(packageName: String, version: String) {
        val cacheFile = File(Config.CACHE_DIR, "${packageName.substringAfterLast("/")}@$version.tar.gz")
        if (!cacheFile.exists()) {
            if (!downloadTarball(packageName, version, cacheFile)) {
                return
            }
        }

        if (cacheFile.exists()) {
            try {
                extract(cacheFile)
            } catch (e: Exception) {
                System.err.println("⚠️  Error extracting $packageName: ${e.message}")
            }
        }
    }

    /**
     * Download tarball by first querying npm registry for the actual URL
     */
    private fun downloadTarball(packageName: String, version: String, outputFile: File): Boolean {
        return try {
            val registryUrl = "$NPM_REGISTRY/${packageName.replace("/", "%2f")}/$version"
            val packageJsonStr = downloadString(registryUrl)

            val packageJson = json.parseToJsonElement(packageJsonStr).jsonObject
            val tarballUrl = packageJson["dist"]?.jsonObject?.get("tarball")?.jsonPrimitive?.content

            if (tarballUrl == null) {
                System.err.println("⚠️  No tarball URL found for $packageName@$version")
                return false
            }

            downloadFile(tarballUrl, outputFile)
            true
        } catch (e: Exception) {
            System.err.println("⚠️  Failed to fetch package info for $packageName: ${e.message}")
            false
        }
    }

    /**
     * Download string content from URL
     */
    private fun downloadString(url: String): String {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                throw Exception("HTTP ${response.statusCode()}")
            }
            response.body()
        } catch (e: Exception) {
            throw Exception("Failed to download from $url: ${e.message}")
        }
    }

    /**
     * Download file from URL
     */
    private fun downloadFile(url: String, out: File): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) {
                System.err.println("⚠️  HTTP ${response.statusCode()} for $url")
                return false
            }
            response.body().use { input ->
                out.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            System.err.println("⚠️  Download error for $url: ${e.message}")
            false
        }
    }

    /**
     * Extract images from tarball directly to working directory
     */
    private fun extract(archive: File) {
        archive.inputStream()
            .let(::BufferedInputStream)
            .let(::GZIPInputStream)
            .let(::TarArchiveInputStream)
            .use { stream ->
                for (entry in generateSequence(stream::getNextEntry)) {
                    if (entry.isDirectory) continue
                    EXTRACT_REGEX.find(entry.name)?.destructured?.let { (theme, filename, ext) ->
                        val nameWithoutExt = filename.substringBeforeLast(".")
                        val parts = nameWithoutExt.split("-")
                        parts.getOrNull(0)?.let { brand ->
                            val out = File(
                                Config.INPUT_ICONS_DIR,
                                listOf(theme)
                                    .plus(parts.drop(1))
                                    .joinToString("-", prefix = "$brand/", postfix = ".$ext")
                            )
                            out.parentFile?.mkdirs()
                            out.outputStream().use { stream.copyTo(it) }
                        }
                    }
                }
            }
    }

    private val EXTRACT_REGEX = Regex("package/(dark|light)/([^/]+)\\.(png|webp)$", RegexOption.IGNORE_CASE)
}