package net.notjustanna.patcher

import kotlinx.serialization.ExperimentalSerializationApi
import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.utils.Processor
import net.notjustanna.patcher.utils.Downloader
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        println("🚀 Starting icon patcher")

        val brandFilter = args.filter { !it.startsWith("--") }.toSet()

        // Step 1: Download packages
        Downloader.run()

        System.gc() // Suggest garbage collection before heavy processing

        // Step 2: Scan and process raster images (avatarfit + backgrounds)
        println("🎨 Processing images...")
        val rasterJobs = Processor.scanJobs(brandFilter)
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
    
    println("✅ Generated ${indexFile.absolutePath}")
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
