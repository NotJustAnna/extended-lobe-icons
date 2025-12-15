package net.notjustanna.patcher.models

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.processors.AvatarFit
import net.notjustanna.patcher.processors.BackgroundGenerator
import net.notjustanna.patcher.processors.Compositor
import net.notjustanna.patcher.utils.Colorimetry
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Job for processing all images in a brand directory
 */
class ImageProcessingJob(private val brandDir: File) : Runnable {
    override fun run() {
        try {
            val brandName = brandDir.name
            val outputIconsDir = File(Config.OUTPUT_ICONS_DIR, brandName)
            outputIconsDir.mkdirs()

            // Find all image files

            val imageFiles = brandDir.listFiles()
                ?.filter { file ->
                    file.isFile
                            && file.extension.lowercase() in Config.RASTER_EXTENSIONS
                            && file.nameWithoutExtension
                        .split('-')
                        .all { it !in Config.IGNORE_PROPERTIES }
                } ?: return

            // Find the color file and detect color once
            val (colorFiles, regularFiles) = imageFiles.partition { it.name.contains("-color") }
            val colorDetection = colorFiles.firstNotNullOfOrNull { file ->
                try {
                    if (Config.IS_DEVELOPMENT) {
                        println("      🔍 Detecting color from ${brandDir.name} using ${file}...")
                    }
                    Colorimetry.detect(ImageIO.read(file)).takeIf {
                        it.type != DetectionType.NONE
                    }
                } catch (e: Exception) {
                    System.err.println("      ⚠️  Error detecting color from ${file.name}: ${e.message}")
                    null
                }
            } ?: brandColorFallbacks[brandName.lowercase()]

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

    private fun processImage(file: File, colorDetect: ColorDetection?, outputDir: File) {
        try {
            val fileName = file.name
            val format = file.extension
            val ext = ".$format"
            val nameWithoutExt = file.nameWithoutExtension

            val image = ImageIO.read(file) ?: return
            val avatarfitImage = AvatarFit.apply(image)

            // Standard black/white background variants
            val isDark = fileName.startsWith("dark")
            val bgColor = if (isDark) Color.BLACK else Color.WHITE
            val standardBg = BackgroundGenerator.createSolid(image.width, image.height, bgColor)

            val variants = mutableMapOf(
                "-avatarfit" to avatarfitImage,
                "-bg" to Compositor.apply(standardBg, image),
                "-bg-avatarfit" to Compositor.apply(standardBg, avatarfitImage)
            )

            // Add brand color variants if color detection is available
            if (colorDetect != null) {
                val brandBg = createBrandBackground(colorDetect, image.width, image.height)

                if (brandBg != null) {
                    variants["-color-avatarfit"] = avatarfitImage
                    variants["-colorbg"] = Compositor.apply(brandBg, image)
                    variants["-colorbg-avatarfit"] = Compositor.apply(brandBg, avatarfitImage)
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
        private val brandColorFallbacks = mapOf(
            "openai" to ColorDetection(
                type = DetectionType.SOLID,
                solidColor = Color(0x00A67E)
            ),
            "openrouter" to ColorDetection(
                type = DetectionType.SOLID,
                solidColor = Color(0x94A3B8)
            ),
        )
    }
}