package net.notjustanna.patcher.processors

import net.notjustanna.patcher.config.Config
import java.awt.RenderingHints.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.sqrt

/**
 * Apply avatar fit (ellipse shrinkwrap + scale-to-fill) to images.
 *
 * The algorithm:
 *   1. Find the content bounding box (pixels with alpha above threshold).
 *   2. Pre-compute the set of **boundary content pixels** — content pixels
 *      with at least one non-content 4-neighbor, or that lie on the image
 *      edge. For a connected shape this set is equivalent-for-containment
 *      to the full content set, but much smaller (≈ perimeter, not area).
 *   3. Starting from an ellipse that tightly circumscribes the bounding box
 *      (radii = half-extent × √2), shrink it as long as every boundary pixel
 *      remains inside the ellipse. Two passes: isotropic, then round-robin
 *      edge-by-edge.
 *   4. Expand the fitted ellipse by the configured padding percent, then
 *      scale-translate the image so that ellipse fills the canvas.
 */
object AvatarFit {
    /**
     * Apply avatarfit to an image. Returns a new BufferedImage of the same
     * dimensions with the content scaled so its fitted ellipse fills the
     * canvas (minus [Config.AVATAR_PADDING_PERCENT] padding).
     */
    fun apply(image: BufferedImage): BufferedImage {
        val contentBounds = findContentBounds(image) ?: return image

        // Boundary pixels: cached once, used for every containment check.
        val boundary = findBoundaryPixels(image)
        if (boundary.isEmpty()) return image

        // Initial ellipse: radii = half-extent × √2 circumscribes the bbox
        // rectangle exactly (corners touch the ellipse). Boundary-pixel
        // containment is rigorous so we don't need the old √2.5 fudge factor.
        var ellipse = createInitialEllipse(contentBounds)

        // Shrink isotropically, then fine-tune each edge independently.
        ellipse = generalShrinkwrap(boundary, ellipse)
        ellipse = roundRobinShrinkwrap(boundary, ellipse)

        // Add padding, then compute transform to scale-fill.
        val paddedEllipse = applyPadding(ellipse)
        val transform = calculateTransform(
            contentBounds,
            paddedEllipse,
            image.width,
            image.height
        )

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

    /**
     * Collect boundary content pixels as an interleaved `[x0, y0, x1, y1, ...]`
     * IntArray. A pixel is "boundary" if it is itself content AND either lies
     * on the image edge or has at least one non-content 4-neighbor.
     *
     * For a simply-connected shape, the set of boundary pixels is sufficient
     * to test ellipse containment — if every boundary pixel is inside an
     * ellipse, every interior pixel is too (interior pixels are convexly
     * surrounded by boundary pixels). For shapes with holes the inner
     * boundary is also captured, which is correct: holes don't affect the
     * outer containment test.
     */
    private fun findBoundaryPixels(image: BufferedImage): IntArray {
        val w = image.width
        val h = image.height
        // Worst case (checkerboard-ish): every content pixel is a boundary
        // pixel. Allocate for that and trim at the end.
        val buf = IntArray(w * h * 2)
        var n = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!isContentPixel(image, x, y)) continue
                val onEdge = x == 0 || y == 0 || x == w - 1 || y == h - 1
                val isBoundary = onEdge ||
                    !isContentPixel(image, x - 1, y) ||
                    !isContentPixel(image, x + 1, y) ||
                    !isContentPixel(image, x, y - 1) ||
                    !isContentPixel(image, x, y + 1)
                if (isBoundary) {
                    buf[n++] = x
                    buf[n++] = y
                }
            }
        }
        return buf.copyOf(n)
    }

    private fun isContentPixel(image: BufferedImage, x: Int, y: Int): Boolean {
        return (image.getRGB(x, y) shr 24) and 0xff > Config.CONTENT_ALPHA_THRESHOLD
    }

    private fun createInitialEllipse(bounds: Bounds): EllipseParams {
        // √2 exactly circumscribes the bounding rectangle — the rectangle's
        // corners sit on the ellipse. Rigorous boundary-pixel containment
        // means no fudge factor is needed.
        val factor = sqrt(2.0)
        return EllipseParams(
            centerX = bounds.centerX,
            centerY = bounds.centerY,
            radiusX = (bounds.width / 2.0) * factor,
            radiusY = (bounds.height / 2.0) * factor
        )
    }

    private fun generalShrinkwrap(
        boundary: IntArray,
        initialEllipse: EllipseParams
    ): EllipseParams {
        var current = initialEllipse
        val shrinkStep = 0.5
        val minRadius = 1.0

        while (current.radiusX > minRadius && current.radiusY > minRadius) {
            val test = current.copy(
                radiusX = current.radiusX - shrinkStep,
                radiusY = current.radiusY - shrinkStep
            )
            if (containsAll(boundary, test)) {
                current = test
            } else {
                break
            }
        }

        return current
    }

    private fun roundRobinShrinkwrap(
        boundary: IntArray,
        initialEllipse: EllipseParams
    ): EllipseParams {
        var current = initialEllipse
        val shrinkStep = 0.25
        var anyProgress = true

        while (anyProgress) {
            anyProgress = false

            // Shrink left edge: push center right, reduce radiusX
            val testLeft = current.copy(
                centerX = current.centerX + shrinkStep / 2,
                radiusX = current.radiusX - shrinkStep / 2
            )
            if (current.radiusX > 1 && containsAll(boundary, testLeft)) {
                current = testLeft
                anyProgress = true
            }

            // Shrink right edge: push center left, reduce radiusX
            val testRight = current.copy(
                centerX = current.centerX - shrinkStep / 2,
                radiusX = current.radiusX - shrinkStep / 2
            )
            if (current.radiusX > 1 && containsAll(boundary, testRight)) {
                current = testRight
                anyProgress = true
            }

            // Shrink top edge: push center down, reduce radiusY
            val testTop = current.copy(
                centerY = current.centerY + shrinkStep / 2,
                radiusY = current.radiusY - shrinkStep / 2
            )
            if (current.radiusY > 1 && containsAll(boundary, testTop)) {
                current = testTop
                anyProgress = true
            }

            // Shrink bottom edge: push center up, reduce radiusY
            val testBottom = current.copy(
                centerY = current.centerY - shrinkStep / 2,
                radiusY = current.radiusY - shrinkStep / 2
            )
            if (current.radiusY > 1 && containsAll(boundary, testBottom)) {
                current = testBottom
                anyProgress = true
            }
        }

        return current
    }

    /**
     * Does [ellipse] contain every boundary pixel in [boundary]? O(n) in the
     * boundary-pixel count — a multiply-add per pixel, no trig.
     */
    private fun containsAll(boundary: IntArray, ellipse: EllipseParams): Boolean {
        val rx2 = ellipse.radiusX * ellipse.radiusX
        val ry2 = ellipse.radiusY * ellipse.radiusY
        var i = 0
        while (i < boundary.size) {
            val dx = boundary[i] - ellipse.centerX
            val dy = boundary[i + 1] - ellipse.centerY
            if (dx * dx / rx2 + dy * dy / ry2 > 1.0) return false
            i += 2
        }
        return true
    }

    private fun applyPadding(ellipse: EllipseParams): EllipseParams {
        // Increase the ellipse to create padding space
        val factor = 1.0 + (Config.AVATAR_PADDING_PERCENT / 100.0)
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
            setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
            setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY)
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
