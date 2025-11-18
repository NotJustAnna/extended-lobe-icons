package net.notjustanna.patcher.utils

import net.notjustanna.patcher.config.Config
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.*

data class GradientStop(val position: Float, val color: Color)
data class LinearGradient(val angle: Double, val stops: List<GradientStop>)
data class ColorSample(val x: Int, val y: Int, val color: Color, val lab: LabColor)
data class ContentBounds(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val width: Int, val height: Int)

class LinearGradientReconstructor(private val image: BufferedImage) {
    private val imageWidth = image.width
    private val imageHeight = image.height

    private val transparencyThreshold = 30
    private val targetSampleRate = 0.1 // 10% of content pixels

    // Content bounds - calculated once
    private val contentBounds: ContentBounds by lazy { calculateContentBounds() }
    private val contentCenterX: Double by lazy { (contentBounds.minX + contentBounds.maxX) / 2.0 }
    private val contentCenterY: Double by lazy { (contentBounds.minY + contentBounds.maxY) / 2.0 }

    // Cache
    private val labCache = mutableMapOf<Color, LabColor>()

    fun reconstructGradient(): LinearGradient? {
        if (Config.IS_DEVELOPMENT) println("Starting gradient reconstruction for ${imageWidth}x${imageHeight} image")

        // Check if we have content
        if (contentBounds.width <= 0 || contentBounds.height <= 0) {
            if (Config.IS_DEVELOPMENT) println("No content found in image")
            return null
        }

        if (Config.IS_DEVELOPMENT) println("Content bounds: ${contentBounds.width}x${contentBounds.height} at (${contentBounds.minX}, ${contentBounds.minY})")

        // Step 1: Collect samples once from entire content area
        val samples = collectContentSamples()
        if (samples.size < 10) {
            if (Config.IS_DEVELOPMENT) println("Not enough valid pixels found (${samples.size})")
            return null
        }

        if (Config.IS_DEVELOPMENT) println("Collected ${samples.size} samples (${(samples.size * 100.0 / (contentBounds.width * contentBounds.height)).format(2)}% of content)")

        // Step 2: Test multiple angles and find the best one
        val (bestAngle, bestScore) = findBestGradientAngle(samples)

        if (bestScore.variance < 1.0) {
            if (Config.IS_DEVELOPMENT) println("No gradient detected - insufficient color variance (${bestScore.variance.format(2)}) -- Consistency was ${bestScore.consistency.format(2)}")
            return null
        }

        if (bestScore.consistency < 0.4) {
            if (Config.IS_DEVELOPMENT) println("No gradient detected - insufficient gradient consistency (${bestScore.consistency.format(2)})")
            return null
        }

        if (Config.IS_DEVELOPMENT) println("Best gradient angle: ${bestAngle * 180 / PI}° (variance: ${bestScore.variance.format(2)}, consistency: ${bestScore.consistency.format(2)})")

        // Step 3: Extract gradient stops along best angle
        val stops = extractGradientStops(samples, bestAngle)

        return if (stops.size >= 2) {
            LinearGradient(bestAngle, stops)
        } else {
            if (Config.IS_DEVELOPMENT) println("Could not extract enough gradient stops")
            null
        }
    }

    private data class AngleScore(val variance: Double, val consistency: Double)

    private fun calculateContentBounds(): ContentBounds {
        var minX = imageWidth
        var maxX = -1
        var minY = imageHeight
        var maxY = -1

        // Quick scan to find bounds
        val scanStep = 3

        for (y in 0 until imageHeight step scanStep) {
            for (x in 0 until imageWidth step scanStep) {
                val color = Color(image.getRGB(x, y), true)
                if (color.alpha > transparencyThreshold) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        // Refine edges
        for (y in maxOf(0, minY - scanStep)..minOf(imageHeight - 1, maxY + scanStep)) {
            for (x in maxOf(0, minX - scanStep)..minOf(imageWidth - 1, maxX + scanStep)) {
                val color = Color(image.getRGB(x, y), true)
                if (color.alpha > transparencyThreshold) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        val width = if (maxX >= minX) maxX - minX + 1 else 0
        val height = if (maxY >= minY) maxY - minY + 1 else 0

        return ContentBounds(minX, maxX, minY, maxY, width, height)
    }

    private fun collectContentSamples(): List<ColorSample> {
        val samples = mutableListOf<ColorSample>()

        // Calculate step size for ~10% coverage
        val totalContentPixels = contentBounds.width * contentBounds.height
        val targetSamples = (totalContentPixels * targetSampleRate).toInt().coerceAtLeast(100)
        val step = sqrt(totalContentPixels.toDouble() / targetSamples).toInt().coerceAtLeast(1)

        // Evenly sample the content area
        for (y in contentBounds.minY..contentBounds.maxY step step) {
            for (x in contentBounds.minX..contentBounds.maxX step step) {
                val color = Color(image.getRGB(x, y), true)
                if (color.alpha > transparencyThreshold) {
                    val rgbColor = Color(color.red, color.green, color.blue)
                    samples.add(ColorSample(x, y, rgbColor, getLabColor(rgbColor)))
                }
            }
        }

        return samples
    }

    private fun findBestGradientAngle(samples: List<ColorSample>): Pair<Double, AngleScore> {
        var bestAngle = 0.0
        var bestScore = AngleScore(0.0, 0.0)

        // Test angles from 0 to 180 degrees
        for (angleDeg in 0 until 180 step 5) {
            val angle = angleDeg * PI / 180.0
            val score = scoreGradientAngle(samples, angle)

            // Best angle has high variance (color changes) and high consistency (smooth gradient)
            val combinedScore = score.variance * score.consistency
            val bestCombined = bestScore.variance * bestScore.consistency

            if (combinedScore > bestCombined) {
                bestAngle = angle
                bestScore = score
            }
        }

        // Refine around best angle
        for (angleDeg in -4..4) {
            val angle = bestAngle + (angleDeg * PI / 180.0)
            val score = scoreGradientAngle(samples, angle)

            val combinedScore = score.variance * score.consistency
            val bestCombined = bestScore.variance * bestScore.consistency

            if (combinedScore > bestCombined) {
                bestAngle = angle
                bestScore = score
            }
        }

        return bestAngle to bestScore
    }

    private fun scoreGradientAngle(samples: List<ColorSample>, angle: Double): AngleScore {
        val dx = cos(angle)
        val dy = sin(angle)

        // Project samples onto this angle
        val projections = samples.map { sample ->
            val projection = (sample.x - contentCenterX) * dx + (sample.y - contentCenterY) * dy
            projection to sample
        }

        // Sort by projection to get gradient order
        val sorted = projections.sortedBy { it.first }

        // Calculate variance (how much colors change along this axis)
        var totalVariance = 0.0
        val buckets = mutableMapOf<Int, MutableList<LabColor>>()

        // Group into buckets along gradient
        val numBuckets = 20
        val minProj = sorted.first().first
        val maxProj = sorted.last().first
        val range = maxProj - minProj

        if (range <= 0) return AngleScore(0.0, 0.0)

        sorted.forEach { (proj, sample) ->
            val bucketIndex = ((proj - minProj) / range * numBuckets).toInt().coerceIn(0, numBuckets - 1)
            buckets.getOrPut(bucketIndex) { mutableListOf() }.add(sample.lab)
        }

        // Calculate variance between bucket averages
        val bucketAverages = buckets.entries
            .sortedBy { it.key }
            .map { it.value }
            .filter { it.isNotEmpty() }
            .map { averageLabColor(it) }

        if (bucketAverages.size < 2) return AngleScore(0.0, 0.0)

        // Variance: difference between first and last colors
        totalVariance = bucketAverages.first().deltaE76(bucketAverages.last())

        // Consistency: how smooth is the gradient?
        var consistency = 1.0

        // Check if colors within each bucket are similar (they should be for a gradient)
        var totalBucketVariance = 0.0
        var bucketCount = 0

        buckets.values.forEach { colors ->
            if (colors.size > 1) {
                val avg = averageLabColor(colors)
                val bucketVariance = colors.sumOf { it.deltaE76(avg) } / colors.size
                totalBucketVariance += bucketVariance
                bucketCount++
            }
        }

        if (bucketCount > 0) {
            val avgBucketVariance = totalBucketVariance / bucketCount
            // Lower variance within buckets = higher consistency
            consistency = 1.0 / (1.0 + avgBucketVariance / 10.0)
        }

        // Also check for smooth color progression
        if (bucketAverages.size > 2) {
            val steps = mutableListOf<Double>()
            for (i in 1 until bucketAverages.size) {
                steps.add(bucketAverages[i - 1].deltaE76(bucketAverages[i]))
            }

            val avgStep = steps.average()
            val stepVariance = steps.map { (it - avgStep) * (it - avgStep) }.average()

            // Lower step variance = smoother gradient
            val smoothness = 1.0 / (1.0 + sqrt(stepVariance) / avgStep)
            consistency *= smoothness
        }

        return AngleScore(totalVariance, consistency)
    }

    private fun extractGradientStops(samples: List<ColorSample>, angle: Double): List<GradientStop> {
        val dx = cos(angle)
        val dy = sin(angle)

        // Project all samples
        val projections = samples.map { sample ->
            val projection = (sample.x - contentCenterX) * dx + (sample.y - contentCenterY) * dy
            projection to sample.color
        }

        // Find range
        val minProj = projections.minOf { it.first }
        val maxProj = projections.maxOf { it.first }
        val range = maxProj - minProj

        if (range <= 0) return emptyList()

        // Normalize projections
        val normalized = projections.map { (proj, color) ->
            ((proj - minProj) / range).toFloat() to color
        }

        // Group into bins
        val numBins = 20
        val bins = Array(numBins) { mutableListOf<Color>() }

        normalized.forEach { (position, color) ->
            val binIndex = (position * numBins).toInt().coerceIn(0, numBins - 1)
            bins[binIndex].add(color)
        }

        // Create stops from non-empty bins
        val stops = mutableListOf<GradientStop>()

        bins.forEachIndexed { index, colors ->
            if (colors.isNotEmpty()) {
                val position = index.toFloat() / numBins
                val avgColor = averageColors(colors)
                stops.add(GradientStop(position, avgColor))
            }
        }

        if (stops.isEmpty()) return emptyList()

        // Simplify: merge very similar adjacent stops
        val simplified = mutableListOf<GradientStop>()
        simplified.add(GradientStop(0f, stops.first().color))

        var lastAdded = stops.first()
        for (i in 1 until stops.size - 1) {
            val current = stops[i]
            val deltaE = getLabColor(lastAdded.color).deltaE76(getLabColor(current.color))

            // Only add if color is different enough
            if (deltaE > 5.0) {
                simplified.add(current)
                lastAdded = current
            }
        }

        simplified.add(GradientStop(1f, stops.last().color))

        // If we ended up with too many stops, reduce to the most important ones
        if (simplified.size > 5) {
            return reduceStops(simplified, 5)
        }

        return simplified
    }

    private fun reduceStops(stops: List<GradientStop>, maxStops: Int): List<GradientStop> {
        if (stops.size <= maxStops) return stops

        // Always keep first and last
        val reduced = mutableListOf<GradientStop>()
        reduced.add(stops.first())

        // Find the most significant color changes
        val significantStops = stops.subList(1, stops.size - 1)
            .map { stop ->
                // Find nearest neighbors
                val prevStop = stops.filter { it.position < stop.position }.maxByOrNull { it.position }
                val nextStop = stops.filter { it.position > stop.position }.minByOrNull { it.position }

                val importance = if (prevStop != null && nextStop != null) {
                    val prevDelta = getLabColor(stop.color).deltaE76(getLabColor(prevStop.color))
                    val nextDelta = getLabColor(stop.color).deltaE76(getLabColor(nextStop.color))
                    prevDelta + nextDelta
                } else {
                    0.0
                }

                stop to importance
            }
            .sortedByDescending { it.second }
            .take(maxStops - 2)
            .map { it.first }
            .sortedBy { it.position }

        reduced.addAll(significantStops)
        reduced.add(stops.last())

        return reduced
    }

    private fun averageLabColor(labs: List<LabColor>): LabColor {
        if (labs.isEmpty()) return LabColor(0.0, 0.0, 0.0)

        val avgL = labs.sumOf { it.l } / labs.size
        val avgA = labs.sumOf { it.a } / labs.size
        val avgB = labs.sumOf { it.b } / labs.size

        return LabColor(avgL, avgA, avgB)
    }

    private fun averageColors(colors: List<Color>): Color {
        if (colors.isEmpty()) return Color.BLACK

        // Use LAB space for better averaging
        val labs = colors.map { getLabColor(it) }
        val avgLab = averageLabColor(labs)

        // Convert back to RGB (simplified - you may want proper LAB to RGB conversion)
        val avgRed = colors.sumOf { it.red } / colors.size
        val avgGreen = colors.sumOf { it.green } / colors.size
        val avgBlue = colors.sumOf { it.blue } / colors.size

        return Color(
            avgRed.coerceIn(0, 255),
            avgGreen.coerceIn(0, 255),
            avgBlue.coerceIn(0, 255)
        )
    }

    private fun getLabColor(color: Color): LabColor {
        return labCache.computeIfAbsent(color) {
            LabColor.fromRgb(color)
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
