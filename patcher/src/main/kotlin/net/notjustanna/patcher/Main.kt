package net.notjustanna.patcher

import kotlinx.serialization.ExperimentalSerializationApi
import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.manifest.BrandManifest
import net.notjustanna.patcher.manifest.ShaManifest
import net.notjustanna.patcher.utils.JobScanner
import net.notjustanna.patcher.utils.Downloader
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        println("🚀 Starting icon patcher (version ${BuildConfig.VERSION})")

        Config.IS_DEVELOPMENT = args.any { it == "--dev" }

        val brandFilter = args.filter { !it.startsWith("--") }.toSet()

        // Load the prior manifest (if any) BEFORE anything mutates packages/.
        // This must happen before prepareWorkingDirectories() because the
        // working/ wipe has no effect on packages/, but reading the manifest
        // before any side effects keeps the ordering obvious.
        val previousManifest = ShaManifest.loadOrNull(File(Config.PACKAGES_DIR, "sha.json"))
        if (previousManifest != null) {
            println("  📖 Loaded prior sha.json (patcher ${previousManifest.patcherVersion}, ${previousManifest.brands.size} brands)")
        }

        // Step 0: Prepare working directories. Done here, not in a Config
        // init block, so simply referencing Config doesn't wipe state — that
        // used to be a footgun for exploratory code and tests.
        Config.prepareWorkingDirectories()

        // Step 1: Download packages
        val upstreamPackages = Downloader.run()

        // Constrained-RAM CI (GitHub Actions free runners, ~7GB) benefits from
        // a GC hint between the Downloader phase (holds tarball buffers) and
        // the image-processing allocation surge. System.gc() is a hint, not a
        // guarantee — but empirically it helps reduce peak heap on CI.
        System.gc()

        // Step 2: Scan and process raster images (avatar + backgrounds)
        println("🎨 Processing images...")
        val rasterJobs = JobScanner.scanJobs(
            brandFilter = brandFilter,
            previousManifest = previousManifest,
            currentPatcherVersion = BuildConfig.VERSION,
        )
        println("  Discovered ${rasterJobs.size} brands")

        if (rasterJobs.isNotEmpty()) {
            if (Config.IS_DEVELOPMENT) {
                rasterJobs.forEach { it.run() }
            } else {
                rasterJobs.map { Thread.ofVirtual().start(it) }.forEach { it.join() }
            }
        }
        println("✅ Processed ${rasterJobs.size} jobs")

        // Step 4: Copy to packages
        copyToPackages()

        // Step 5: Generate index.json
        generateIndexJson()

        // Step 6: Generate sha.json
        generateShaJson(upstreamPackages)

        println("🎉 Icon patching complete!")
        exitProcess(0)
    } catch (e: Exception) {
        System.err.println("❌ Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

/**
 * Copy working directory to packages/ (flattened)
 */
private fun copyToPackages() {
    println("📁 Copying to packages/ directory...")

    if (Config.PACKAGES_DIR.exists()) {
        println("  🗑️  Removing existing packages/")
        Config.PACKAGES_DIR.deleteRecursively()
    }

    if (!Config.WORKING_DIR.exists()) {
        println("  ⚠️  Working directory not found")
        return
    }

    var totalMerged = 0
    Config.WORKING_DIR.listFiles()?.forEach { entry ->
        if (entry.isDirectory) {
            entry.copyRecursively(Config.PACKAGES_DIR, overwrite = true)
            totalMerged++
        }
    }

    println("✅ Copy complete")
}

@Serializable
data class FileNode(
    val name: String,
    val type: String,
    val children: List<FileNode>? = null
)

/**
 * Generate index.json with directory/file structure of packages/
 */
@OptIn(ExperimentalSerializationApi::class)
private fun generateIndexJson() {
    println("📋 Generating index.json...")

    if (!Config.PACKAGES_DIR.exists()) {
        println("  ⚠️  Packages directory not found")
        return
    }

    val rootNode = buildFileTree(Config.PACKAGES_DIR, Config.PACKAGES_DIR.name)

    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    val indexFile = File(Config.PACKAGES_DIR, "index.json")
    indexFile.writeText(json.encodeToString(rootNode))

    println("✅ Generated ${indexFile.normalize().absoluteFile.absolutePath}")
}

/**
 * Recursively build file tree structure
 */
private fun buildFileTree(file: File, name: String): FileNode {
    return if (file.isDirectory) {
        val children = file.listFiles()
            ?.sortedWith(compareBy({ it.isFile }, { it.name }))
            ?.map { buildFileTree(it, it.name) }
            ?: emptyList()
        FileNode(name = name, type = "directory", children = children)
    } else {
        FileNode(name = name, type = "file")
    }
}

/**
 * Generate packages/sha.json recording, per brand, the SHA-256 of each
 * upstream source file used to produce the currently published outputs plus
 * the patcher version. The next run consults this manifest to skip brands
 * whose inputs haven't changed.
 */
private fun generateShaJson(upstreamPackages: List<Pair<String, String>>) {
    println("🔐 Generating sha.json...")

    if (!Config.INPUT_ICONS_DIR.exists()) {
        println("  ⚠️  Input directory not found, skipping sha.json")
        return
    }

    val brandDirs = Config.INPUT_ICONS_DIR.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name }
        ?: emptyList()

    val brands = brandDirs.associate { brandDir ->
        val sources = brandDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.associate { it.name to ShaManifest.sha256(it) }
            ?: emptyMap()
        brandDir.name to BrandManifest(sources = sources)
    }

    val manifest = ShaManifest(
        patcherVersion = BuildConfig.VERSION,
        generatedAt = Instant.now().toString(),
        upstreamPackages = upstreamPackages.toMap().toSortedMap(),
        brands = brands.toSortedMap(),
    )

    val outFile = File(Config.PACKAGES_DIR, "sha.json")
    ShaManifest.save(manifest, outFile)

    println("✅ Generated ${outFile.normalize().absoluteFile.absolutePath}")
}
