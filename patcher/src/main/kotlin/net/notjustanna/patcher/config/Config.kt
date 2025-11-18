package net.notjustanna.patcher.config

import java.io.File

/**
 * Global configuration for icon patcher
 */
object Config {
    // Working directory structure
    val WORKING_DIR: File = File("working")
    val INPUT_ICONS_DIR: File = WORKING_DIR.resolve("input/icons")
    val OUTPUT_ICONS_DIR: File = WORKING_DIR.resolve("output/icons")
    val PACKAGES_DIR: File = File("../packages")
    val CACHE_DIR: File = File("cache")
    val UPSTREAM_PACKAGE_JSON: File = File("upstream/package.json")

    // Supported formats
    val RASTER_EXTENSIONS = listOf("png", "webp")
    val IGNORE_PROPERTIES = listOf("text", "brand", "cn")

    // Circle fitting configuration for avatar images
    const val CIRCLE_FIT_PADDING_PERCENTAGE = 10
    const val CIRCLE_FIT_MIN_ALPHA_TOLERANCE = 127

    // Gradient detection configuration
    const val MAX_COLOR_STOPS = 3
    const val DELTA_E_TOLERANCE = 2.3
    const val EDGE_SCAN_DIRECTIONS = 16

    // Environment checks
    var IS_DEVELOPMENT = System.getenv("NODE_ENV") == "development" ||
                         System.getProperty("dev") == "true"

    init {
        // Ensure required directories exist
        WORKING_DIR.deleteRecursively()
        WORKING_DIR.mkdirs()
        OUTPUT_ICONS_DIR.mkdirs()
        CACHE_DIR.mkdirs()
    }
}
