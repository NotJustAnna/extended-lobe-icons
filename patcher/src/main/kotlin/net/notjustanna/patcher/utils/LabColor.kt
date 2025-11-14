package net.notjustanna.patcher.utils

import java.awt.Color
import kotlin.math.sqrt

/**
 * LAB color space representation (CIELAB)
 */
data class LabColor(
    val l: Double,
    val a: Double,
    val b: Double
) {
    companion object {
        fun fromRgb(color: Color): LabColor {
            return fromRgb(color.red, color.green, color.blue)
        }

        fun deltaE76(c1: Color, c2: Color): Double {
            return fromRgb(c1).deltaE76(fromRgb(c2))
        }

        fun fromRgb(r: Int, g: Int, b: Int): LabColor {
            // RGB to XYZ conversion
            var rNorm = r / 255.0
            var gNorm = g / 255.0
            var bNorm = b / 255.0

            rNorm = if (rNorm > 0.04045) Math.pow((rNorm + 0.055) / 1.055, 2.4) else rNorm / 12.92
            gNorm = if (gNorm > 0.04045) Math.pow((gNorm + 0.055) / 1.055, 2.4) else gNorm / 12.92
            bNorm = if (bNorm > 0.04045) Math.pow((bNorm + 0.055) / 1.055, 2.4) else bNorm / 12.92

            val x = (rNorm * 0.4124564 + gNorm * 0.3575761 + bNorm * 0.1804375) * 100
            val y = (rNorm * 0.2126729 + gNorm * 0.7151522 + bNorm * 0.0721750) * 100
            val z = (rNorm * 0.0193339 + gNorm * 0.1191920 + bNorm * 0.9503041) * 100

            // XYZ to LAB conversion
            val xn = 95.047
            val yn = 100.000
            val zn = 108.883

            fun labFunc(t: Double): Double {
                return if (t > 0.008856) Math.pow(t, 1.0 / 3.0) else (7.787 * t + 16.0 / 116.0)
            }

            val fx = labFunc(x / xn)
            val fy = labFunc(y / yn)
            val fz = labFunc(z / zn)

            val l = 116 * fy - 16
            val a = 500 * (fx - fy)
            val b = 200 * (fy - fz)

            return LabColor(l, a, b)
        }
    }

    /**
     * Calculate Delta E (CIE76) color difference
     */
    fun deltaE76(other: LabColor): Double {
        val dl = this.l - other.l
        val da = this.a - other.a
        val db = this.b - other.b
        return sqrt(dl * dl + da * da + db * db)
    }
}