package net.notjustanna.patcher.config

import java.io.File

/**
 * Global configuration for icon patcher.
 *
 * Intentionally has no destructive side effects at object-initialization time.
 * Callers are responsible for preparing working directories via
 * [prepareWorkingDirectories] before running the pipeline.
 */
object Config {
    // Working directory structure
    val WORKING_DIR: File = File("working")
    val INPUT_ICONS_DIR: File = WORKING_DIR.resolve("input/icons")
    val OUTPUT_ICONS_DIR: File = WORKING_DIR.resolve("output/icons")

    /**
     * Where the regenerated package tree is published. Defaults to `../packages`
     * (the repo root's packages/ when run from patcher/), overridable via
     * `-Dpatcher.packagesDir=...` for runs from other working directories.
     */
    val PACKAGES_DIR: File = File(System.getProperty("patcher.packagesDir", "../packages"))
    val CACHE_DIR: File = File("cache")
    val UPSTREAM_PACKAGE_JSON: File = File("upstream/package.json")

    // Supported formats
    val RASTER_EXTENSIONS = listOf("png", "webp")

    /**
     * Upstream filename tokens whose variants are copied through unmodified.
     * `text` wordmarks, `brand` wordmarks, and `cn` (Chinese) variants don't
     * participate in the avatar/background pipeline — they pass through as-is.
     */
    val UPSTREAM_PASSTHROUGH_TOKENS = listOf("text", "brand", "cn")

    // Avatar fitting configuration
    const val AVATAR_PADDING_PERCENT = 20
    const val CONTENT_ALPHA_THRESHOLD = 127

    // Solid-color detection tolerance (CIE76 ΔE in LAB space)
    const val SOLID_COLOR_DELTA_E_TOLERANCE = 2.3

    // Environment checks
    var IS_DEVELOPMENT = System.getenv("NODE_ENV") == "development" ||
                         System.getProperty("dev") == "true"

    /**
     * Wipe and (re)create the working directory tree. Must be called before
     * any pipeline step that reads or writes `working/`.
     *
     * Kept out of an `init {}` block deliberately: merely referencing [Config]
     * used to wipe state, which was a footgun for tests and exploratory code.
     */
    fun prepareWorkingDirectories() {
        WORKING_DIR.deleteRecursively()
        WORKING_DIR.mkdirs()
        OUTPUT_ICONS_DIR.mkdirs()
        CACHE_DIR.mkdirs()
    }
}
