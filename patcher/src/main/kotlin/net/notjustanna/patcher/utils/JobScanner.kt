package net.notjustanna.patcher.utils

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.manifest.ShaManifest
import net.notjustanna.patcher.models.ImageProcessingJob

/**
 * Enumerates per-brand input directories and produces one [ImageProcessingJob]
 * per brand, carrying the prior [ShaManifest] for cache-reuse decisions.
 */
object JobScanner {
    /**
     * Scan for image processing jobs (one job per brand directory).
     */
    fun scanJobs(
        brandFilter: Set<String>,
        previousManifest: ShaManifest?,
        currentPatcherVersion: String,
    ): List<Runnable> {
        if (!Config.INPUT_ICONS_DIR.exists()) {
            return emptyList()
        }

        val allBrands = Config.INPUT_ICONS_DIR.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val brands = if (brandFilter.isEmpty()) {
            allBrands
        } else {
            allBrands.filter { it.name in brandFilter }
        }

        return brands.map { brandDir ->
            ImageProcessingJob(
                brandDir = brandDir,
                previousManifest = previousManifest,
                currentPatcherVersion = currentPatcherVersion,
            )
        }
    }
}
