package net.notjustanna.patcher.models

import java.awt.Color

/**
 * Color detection result
 */
data class ColorDetection(
    val type: DetectionType,
    val solidColor: Color? = null,
    val angle: Double? = null,
    val stops: List<ColorStop>? = null
)

enum class DetectionType {
    SOLID, LINEAR, NONE
}

/**
 * Color stop for gradient
 */
data class ColorStop(
    val color: Color,
    val position: Double
)
