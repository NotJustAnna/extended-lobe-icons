package net.notjustanna.patcher.processors

import net.notjustanna.patcher.models.ColorStop
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import kotlin.Double
import kotlin.Int
import kotlin.let
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure background image generation (no compositing, no I/O)
 */
object BackgroundGenerator {
    /**
     * Generate solid color background
     */
    fun createSolid(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, TYPE_INT_RGB)
        image.createGraphics().let { g ->
            g.color = color
            g.fillRect(0, 0, width, height)
            g.dispose()
        }
        return image
    }

    /**
     * Generate gradient background from color stops and angle
     */
    fun createGradient(
        width: Int,
        height: Int,
        angle: Double,
        colorStops: List<ColorStop>
    ): BufferedImage {
        val angleRad = (angle * Math.PI) / 180
        val dx = cos(angleRad)
        val dy = sin(angleRad)

        val diagonal = sqrt((width * width + height * height).toDouble())
        val startX = width / 2 - (diagonal / 2) * dx
        val startY = height / 2 - (diagonal / 2) * dy
        val endX = width / 2 + (diagonal / 2) * dx
        val endY = height / 2 + (diagonal / 2) * dy

        val svg = """
            <svg width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <linearGradient id="grad" x1="${startX.toInt()}" y1="${startY.toInt()}" x2="${endX.toInt()}" y2="${endY.toInt()}" gradientUnits="userSpaceOnUse">
                        ${colorStops.joinToString("") { stop ->
                            "<stop offset=\"${stop.position * 100}%\" stop-color=\"${stop.color.toHex()}\"/>"
                        }}
                    </linearGradient>
                </defs>
                <rect width="$width" height="$height" fill="url(#grad)"/>
            </svg>
        """.trimIndent()

        var img: BufferedImage? = null
        val directTranscoding = object : ImageTranscoder() {
            init {
                hints.put(KEY_WIDTH, width.toFloat())
                hints.put(KEY_HEIGHT, height.toFloat())
            }
            override fun createImage(w: Int, h: Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            override fun writeImage(image: BufferedImage?, hints: TranscoderOutput?) {
                img = image
            }
        }
        directTranscoding.transcode(TranscoderInput(svg.reader()), null)
        return img!!
    }

    private fun Color.toHex(): String {
        return String.format("#%02x%02x%02x", this.red, this.green, this.blue)
    }
}
