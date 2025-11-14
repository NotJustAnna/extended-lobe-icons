package net.notjustanna.patcher.utils

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.models.ImageProcessingJob

/**
 * Scans icon directories and creates processing jobs
 */
object Processor {
    /**
     * Scan for image processing jobs (one job per brand directory)
     */
    fun scanJobs(brandFilter: Set<String>): List<Runnable> {
        if (!Config.INPUT_ICONS_DIR.exists()) {
            return emptyList()
        }

        val allBrands = Config.INPUT_ICONS_DIR.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val brands = if (brandFilter.isEmpty()) {
            allBrands
        } else {
            allBrands.filter { it.name in brandFilter }
        }

        return brands.map(::ImageProcessingJob)
    }
}