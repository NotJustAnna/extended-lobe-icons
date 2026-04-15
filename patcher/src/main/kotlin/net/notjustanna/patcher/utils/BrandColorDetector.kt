package net.notjustanna.patcher.utils

import net.notjustanna.patcher.config.Config
import net.notjustanna.patcher.models.ColorDetection
import net.notjustanna.patcher.models.ColorStop
import net.notjustanna.patcher.models.DetectionType
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Detects a brand's canonical color (solid or linear gradient) from an image.
 *
 * First attempts to identify a single solid color shared by all visible
 * pixels (within [Config.SOLID_COLOR_DELTA_E_TOLERANCE] in LAB space); falls
 * back to linear-gradient reconstruction if no single color dominates.
 */
object BrandColorDetector {
    /**
     * Detect solid color or linear gradient; returns [DetectionType.NONE] if
     * neither is confidently identifiable.
     */
    fun detect(image: BufferedImage): ColorDetection {
        // Find max alpha for threshold calculation
        val alphaThreshold = findMaxAlpha(image) * 0.95

        // Try to detect solid color first
        val solidColor = detectSolidColor(image, alphaThreshold)
        if (solidColor != null) {
            return ColorDetection(
                type = DetectionType.SOLID,
                solidColor = solidColor
            )
        }

        // Use LinearGradientReconstructor for gradient detection
        val reconstructor = LinearGradientReconstructor(image)
        val gradient = reconstructor.reconstructGradient()

        if (gradient != null && gradient.stops.size >= 2) {
            // Convert GradientStop to ColorStop
            val stops = gradient.stops.map { stop ->
                ColorStop(stop.color, stop.position.toDouble())
            }
            
            return ColorDetection(
                type = DetectionType.LINEAR,
                angle = gradient.angle * 180 / Math.PI,
                stops = stops
            )
        }

        return ColorDetection(type = DetectionType.NONE)
    }

    private fun findMaxAlpha(image: BufferedImage): Int {
        val alphaRaster = image.alphaRaster
        if (alphaRaster == null) {
            return 255
        }
        val w = image.width
        val h = image.height
        return alphaRaster
            .getSamples(0, 0, w, h, 0, IntArray(w * h))
            .maxOf { it }
    }

    /**
     * Detect if all visible pixels are the same solid color
     */
    private fun detectSolidColor(image: BufferedImage, alphaThreshold: Double): Color? {
        val width = image.width
        val height = image.height
        var firstColor: Color? = null
        var firstColorLab: LabColor? = null
        var pixelCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                val a = ((argb ushr 24) and 0xFF).toDouble()
                if (a == 0.0 || a < alphaThreshold) continue

                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF

                pixelCount++

                if (firstColor == null) {
                    val c = Color(r, g, b)
                    firstColor = c
                    firstColorLab = LabColor.fromRgb(c)
                } else {
                    val colorLab = LabColor.fromRgb(r, g, b)
                    if (colorLab.deltaE76(firstColorLab!!) > Config.SOLID_COLOR_DELTA_E_TOLERANCE) {
                        return null // Found different color, not solid
                    }
                }
            }
        }

        return if (pixelCount > 0) firstColor else null
    }
}
