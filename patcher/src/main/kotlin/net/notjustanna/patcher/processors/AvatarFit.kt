package net.notjustanna.patcher.processors

import net.notjustanna.patcher.config.Config
import java.awt.RenderingHints.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Apply avatar fit (circle fitting) to images
 */
object AvatarFit {
    /**
     * Apply avatarfit (ellipse fitting) to an image
     * Returns the fitted BufferedImage ready to composite
     */
    fun apply(image: BufferedImage): BufferedImage {
        // Step 1: Find content boundaries
        val contentBounds = findContentBounds(image)
        if (contentBounds == null) {
            // No content found, return empty image
            return image
        }

        // Step 2: Create initial ellipse inscribing the boundaries
        var ellipse = createInitialEllipse(contentBounds)

        // Step 3: General shrinkwrap
        ellipse = generalShrinkwrap(image, ellipse)

        // Step 4: Fine-tune with round-robin edge reduction
        ellipse = roundRobinShrinkwrap(image, ellipse)

        // Step 5: Apply padding (reducing the ellipse to leave padding space)
        val paddedEllipse = applyPadding(ellipse)

        // Step 6: Calculate transform to fit content into the full-canvas ellipse
        val transform = calculateTransform(
            contentBounds,
            paddedEllipse,
            image.width,
            image.height
        )

        // Step 7: Render with full-canvas ellipse
        return renderImage(image, transform)
    }

    private fun findContentBounds(image: BufferedImage): Bounds? {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        var hasContent = false

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (isContentPixel(image, x, y)) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    hasContent = true
                }
            }
        }

        return if (hasContent) Bounds(minX, minY, maxX, maxY) else null
    }

    private fun isContentPixel(image: BufferedImage, x: Int, y: Int): Boolean {
        return (image.getRGB(x, y) shr 24) and 0xff > Config.CIRCLE_FIT_MIN_ALPHA_TOLERANCE
    }

    private fun createInitialEllipse(bounds: Bounds): EllipseParams {
        // To inscribe a rectangle inside an ellipse, multiply by √2
        val sqrt2 = sqrt(2.5) // Slightly larger to ensure full coverage
        return EllipseParams(
            centerX = bounds.centerX,
            centerY = bounds.centerY,
            radiusX = (bounds.width / 2.0) * sqrt2,
            radiusY = (bounds.height / 2.0) * sqrt2
        )
    }

    private fun generalShrinkwrap(
        image: BufferedImage,
        initialEllipse: EllipseParams
    ): EllipseParams {
        var current = initialEllipse
        val shrinkStep = 0.5 // Shrink by 1 pixel at a time
        val minRadius = 1.0

        while (current.radiusX > minRadius && current.radiusY > minRadius) {
            val test = current.copy(
                radiusX = current.radiusX - shrinkStep,
                radiusY = current.radiusY - shrinkStep
            )

            if (containsAllContentEdgeCheck(image, test)) {
                current = test
            } else {
                break
            }
        }

        return current
    }

    private fun roundRobinShrinkwrap(
        image: BufferedImage,
        initialEllipse: EllipseParams
    ): EllipseParams {
        var current = initialEllipse
        val shrinkStep = 0.25 // Fine-tune with smaller steps
        var anyProgress = true

        while (anyProgress) {
            anyProgress = false

            // Try shrinking left (reduce centerX and radiusX)
            val testLeft = current.copy(
                centerX = current.centerX + shrinkStep / 2,
                radiusX = current.radiusX - shrinkStep / 2
            )
            if (current.radiusX > 1 && containsAllContentEdgeCheck(image, testLeft)) {
                current = testLeft
                anyProgress = true
            }

            // Try shrinking right (increase centerX, reduce radiusX)
            val testRight = current.copy(
                centerX = current.centerX - shrinkStep / 2,
                radiusX = current.radiusX - shrinkStep / 2
            )
            if (current.radiusX > 1 && containsAllContentEdgeCheck(image, testRight)) {
                current = testRight
                anyProgress = true
            }

            // Try shrinking top (reduce centerY and radiusY)
            val testTop = current.copy(
                centerY = current.centerY + shrinkStep / 2,
                radiusY = current.radiusY - shrinkStep / 2
            )
            if (current.radiusY > 1 && containsAllContentEdgeCheck(image, testTop)) {
                current = testTop
                anyProgress = true
            }

            // Try shrinking bottom (increase centerY, reduce radiusY)
            val testBottom = current.copy(
                centerY = current.centerY - shrinkStep / 2,
                radiusY = current.radiusY - shrinkStep / 2
            )
            if (current.radiusY > 1 && containsAllContentEdgeCheck(image, testBottom)) {
                current = testBottom
                anyProgress = true
            }
        }

        return current
    }

    private fun containsAllContentEdgeCheck(
        image: BufferedImage,
        ellipse: EllipseParams
    ): Boolean {
        // Check pixels near the ellipse edge (optimization)
        val checkRadius = 2 // Check within 2 pixels of the edge
        val angleStep = PI / 360 // Check every 0.5 degree

        // First, quick check on ellipse boundary points
        var angle = 0.0
        while (angle < 2 * PI) {
            // Points on and near the ellipse edge
            for (r in -checkRadius..checkRadius) {
                val factor = 1.0 - (r.toDouble() / ellipse.radiusX.coerceAtLeast(ellipse.radiusY))
                val x = (ellipse.centerX + cos(angle) * ellipse.radiusX * factor).toInt()
                val y = (ellipse.centerY + sin(angle) * ellipse.radiusY * factor).toInt()

                if (x in 0 until image.width && y in 0 until image.height) {
                    if (isContentPixel(image, x, y) && !isInEllipse(x, y, ellipse)) {
                        return false
                    }
                }
            }
            angle += angleStep
        }

        return true
    }

    private fun isInEllipse(x: Int, y: Int, ellipse: EllipseParams): Boolean {
        val dx = (x - ellipse.centerX) / ellipse.radiusX
        val dy = (y - ellipse.centerY) / ellipse.radiusY
        return dx * dx + dy * dy <= 1.0
    }

    private fun applyPadding(ellipse: EllipseParams): EllipseParams {
        // Increase the ellipse to create padding space
        val factor = 1.0 + (Config.CIRCLE_FIT_PADDING_PERCENTAGE / 100.0)
        return ellipse.copy(
            radiusX = ellipse.radiusX * factor,
            radiusY = ellipse.radiusY * factor
        )
    }

    private fun calculateTransform(
        contentBounds: Bounds,
        fitEllipse: EllipseParams,
        canvasWidth: Int,
        canvasHeight: Int
    ): AffineTransform {
        // Calculate scale to fit the content ellipse into the full canvas
        val scaleX = canvasWidth / (fitEllipse.radiusX * 2)
        val scaleY = canvasHeight / (fitEllipse.radiusY * 2)
        val scale = minOf(scaleX, scaleY)

        // Calculate translation to center the scaled content
        val scaledWidth = contentBounds.width * scale
        val scaledHeight = contentBounds.height * scale

        val translateX = (canvasWidth - scaledWidth) / 2.0 - contentBounds.minX * scale
        val translateY = (canvasHeight - scaledHeight) / 2.0 - contentBounds.minY * scale

        val transform = AffineTransform()
        transform.translate(translateX, translateY)
        transform.scale(scale, scale)

        return transform
    }

    private fun renderImage(
        originalImage: BufferedImage,
        imageTransform: AffineTransform
    ): BufferedImage {
        val result = BufferedImage(
            originalImage.width,
            originalImage.height,
            TYPE_INT_ARGB
        )

        result.createGraphics().apply {
            // Enable anti-aliasing
            setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
            setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY)

            // Draw the transformed image
            drawImage(originalImage, imageTransform, null)

            dispose()
        }

        return result
    }

    data class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val width: Int get() = maxX - minX + 1
        val height: Int get() = maxY - minY + 1
        val centerX: Double get() = (minX + maxX) / 2.0
        val centerY: Double get() = (minY + maxY) / 2.0
    }

    data class EllipseParams(
        val centerX: Double,
        val centerY: Double,
        val radiusX: Double,
        val radiusY: Double
    )
}
