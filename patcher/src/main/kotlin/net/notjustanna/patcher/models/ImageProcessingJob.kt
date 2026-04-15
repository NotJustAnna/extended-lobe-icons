package net.notjustanna.patcher.models

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.manifest.ShaManifest
import net.notjustanna.patcher.processors.AvatarFit
import net.notjustanna.patcher.processors.BackgroundGenerator
import net.notjustanna.patcher.processors.Compositor
import net.notjustanna.patcher.utils.BrandColorDetector
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Job for processing all images in a brand directory
 */
class ImageProcessingJob(
    private val brandDir: File,
    private val previousManifest: ShaManifest?,
    private val currentPatcherVersion: String,
) : Runnable {
    override fun run() {
        try {
            val brandName = brandDir.name
            val outputIconsDir = File(Config.OUTPUT_ICONS_DIR, brandName)
            outputIconsDir.mkdirs()

            // Short-circuit: if the prior manifest records identical source
            // SHAs AND the same patcher version for this brand, copy the
            // already-published outputs directly rather than re-running the
            // nondeterministic AvatarFit / BrandColorDetector / Batik pipeline.
            if (tryReuseCachedOutputs(brandName, outputIconsDir)) {
                if (Config.IS_DEVELOPMENT) {
                    println("      ♻️  Reused cached outputs for $brandName")
                }
                return
            }

            // Find all image files

            val imageFiles = brandDir.listFiles()
                ?.filter { file ->
                    file.isFile
                            && file.extension.lowercase() in Config.RASTER_EXTENSIONS
                            && file.nameWithoutExtension
                        .split('-')
                        .all { it !in Config.UPSTREAM_PASSTHROUGH_TOKENS }
                } ?: return

            // Find the color file and detect color once
            val (colorFiles, regularFiles) = imageFiles.partition { it.name.contains("-color") }
            val colorDetection = colorFiles.firstNotNullOfOrNull { file ->
                try {
                    if (Config.IS_DEVELOPMENT) {
                        println("      🔍 Detecting color from ${brandDir.name} using ${file}...")
                    }
                    BrandColorDetector.detect(ImageIO.read(file)).takeIf {
                        it.type != DetectionType.NONE
                    }
                } catch (e: Exception) {
                    System.err.println("      ⚠️  Error detecting color from ${file.name}: ${e.message}")
                    null
                }
            } ?: brandColorOverrides[brandName.lowercase()]

            // Process non-color files
            for (imageFile in regularFiles) {
                processImage(imageFile, colorDetection, outputIconsDir)
            }

            for (colorFile in colorFiles) {
                processImage(colorFile, null, outputIconsDir)
            }
        } catch (e: Exception) {
            System.err.println("      ⚠️  Error processing brand ${brandDir.name}: ${e.message}")
        }
    }

    /**
     * Try to reuse previously generated outputs for this brand.
     *
     * Returns true iff:
     *   - a prior manifest exists and its patcherVersion matches the current one,
     *   - the manifest has an entry for this brand,
     *   - every source file currently in brandDir hashes to the value recorded
     *     in the manifest (and no source files were added or removed), and
     *   - the previously-published output directory still exists on disk.
     *
     * When all conditions hold, the previously-published output files are copied
     * byte-for-byte into outputIconsDir, bypassing the nondeterministic pipeline.
     */
    private fun tryReuseCachedOutputs(brandName: String, outputIconsDir: File): Boolean {
        val manifest = previousManifest ?: return false
        if (manifest.patcherVersion != currentPatcherVersion) return false

        val recorded = manifest.brands[brandName]?.sources ?: return false

        val currentSourceFiles = brandDir.listFiles()?.filter { it.isFile } ?: return false
        if (currentSourceFiles.size != recorded.size) return false

        for (file in currentSourceFiles) {
            val expected = recorded[file.name] ?: return false
            if (ShaManifest.sha256(file) != expected) return false
        }

        val publishedDir = File(Config.PACKAGES_DIR, "icons/$brandName")
        if (!publishedDir.isDirectory) return false

        val publishedFiles = publishedDir.listFiles()?.filter { it.isFile } ?: return false
        if (publishedFiles.isEmpty()) return false

        for (f in publishedFiles) {
            val dest = File(outputIconsDir, f.name)
            f.copyTo(dest, overwrite = true)
        }
        return true
    }

    private fun processImage(file: File, colorDetect: ColorDetection?, outputDir: File) {
        try {
            val fileName = file.name
            val format = file.extension
            val ext = ".$format"
            val nameWithoutExt = file.nameWithoutExtension

            val image = ImageIO.read(file) ?: return
            val avatarImage = AvatarFit.apply(image)

            // Standard black/white background variants
            val isDark = fileName.startsWith("dark")
            val bgColor = if (isDark) Color.BLACK else Color.WHITE
            val standardBg = BackgroundGenerator.createSolid(image.width, image.height, bgColor)

            val variants = mutableMapOf(
                "-avatar" to avatarImage,
                "-bg" to Compositor.apply(standardBg, image),
                "-bg-avatar" to Compositor.apply(standardBg, avatarImage)
            )

            // Add brand color variants if color detection is available.
            // Note: the previous "-color-avatar" entry here was a duplicate of
            // "-avatar" under a misleading name — the actual colored-source
            // avatar variant is produced by the separate colorFiles pass.
            if (colorDetect != null) {
                val brandBg = createBrandBackground(colorDetect, image.width, image.height)

                if (brandBg != null) {
                    variants["-colorbg"] = Compositor.apply(brandBg, image)
                    variants["-colorbg-avatar"] = Compositor.apply(brandBg, avatarImage)
                }
            }

            variants.forEach { (suffix, img) ->
                val output = File(outputDir, "$nameWithoutExt$suffix$ext")
                try {
                    ImageIO.write(img, format, output)
                } catch (e: Exception) {
                    System.err.println("Error saving image: ${output.absolutePath} - ${e.message}")
                }
            }
        } catch (e: Exception) {
            System.err.println("      ⚠️  Error processing ${file.name}: ${e.message}")
        }
    }

    private fun createBrandBackground(detection: ColorDetection, width: Int, height: Int): BufferedImage? {
        return when (detection.type) {
            DetectionType.SOLID -> {
                detection.solidColor?.let { BackgroundGenerator.createSolid(width, height, it) }
            }

            DetectionType.LINEAR -> {
                if (detection.angle != null && !detection.stops.isNullOrEmpty()) {
                    BackgroundGenerator.createGradient(width, height, detection.angle, detection.stops)
                } else null
            }

            else -> null
        }
    }

    companion object {
        /**
         * Intentional brand-color choices that take effect when auto-detection
         * returns [DetectionType.NONE]. These are not fallbacks from a failing
         * detector — they're deliberate aesthetic decisions for brands whose
         * upstream color variant doesn't produce the desired brand color.
         */
        private val brandColorOverrides = mapOf(
            // OpenAI's mint brand color; upstream color variant reads as near-black to the detector.
            "openai" to ColorDetection(
                type = DetectionType.SOLID,
                solidColor = Color(0x00A67E)
            ),
            // OpenRouter's slate; upstream lacks a usable color variant.
            "openrouter" to ColorDetection(
                type = DetectionType.SOLID,
                solidColor = Color(0x94A3B8)
            ),
        )
    }
}
