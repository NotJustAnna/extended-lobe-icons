package net.notjustanna.patcher.processors

import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object Compositor {
    /**
     * Composite two images (overlay foreground on background, centered)
     */
    fun apply(
        background: BufferedImage,
        foreground: BufferedImage
    ): BufferedImage {
        return BufferedImage(background.width, background.height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().let { g ->
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
                g.drawImage(background, 0, 0, null)
                val fgX = (background.width - foreground.width) / 2
                val fgY = (background.height - foreground.height) / 2
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)
                g.drawImage(foreground, fgX, fgY, null)
                g.dispose()
            }
        }
    }
}