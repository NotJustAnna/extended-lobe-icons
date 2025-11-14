package net.notjustanna.patcher

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.utils.Processor
import net.notjustanna.patcher.utils.Downloader
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
