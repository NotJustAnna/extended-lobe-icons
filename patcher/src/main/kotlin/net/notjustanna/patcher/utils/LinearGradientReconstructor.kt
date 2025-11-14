package net.notjustanna.patcher.utils

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.*

data class GradientStop(val position: Float, val color: Color)
data class LinearGradient(val angle: Double, val stops: List<GradientStop>)
data class ColorSample(val x: Int, val y: Int, val color: Color, val lab: LabColor)

class LinearGradientReconstructor(private val image: BufferedImage) {
    private val width = image.width
    private val height = image.height
    private val centerX = width / 2.0
    private val centerY = height / 2.0

    private val transparencyThreshold = 30
    private val targetSampleRate = 0.1 // 10% of pixels

    // Cache for performance
    private val labCache = mutableMapOf<Color, LabColor>()

    fun reconstructGradient(): LinearGradient? {
        // println("Starting gradient reconstruction for ${width}x${height} image")

        // Step 1: Collect initial samples for angle detection
        val angleSamples = collectSamplesForAngleDetection()
        if (angleSamples.size < 10) {
            // println("Not enough valid pixels found")
            return null
        }

        // Step 2: Detect gradient angle with high precision
        val gradientAngle = detectGradientAngle(angleSamples)
        // println("Detected gradient angle: ${gradientAngle * 180 / PI}°")

        // Step 3: Sample 10% of the image pixels
        val allSamples = collectImageSamples()
        // println("Collected ${allSamples.size} samples (${(allSamples.size * 100.0 / (width * height)).format(2)}% of image)")

        // Step 4: Project all samples perpendicular to gradient line
        val projectedSamples = projectSamplesOntoGradientAxis(allSamples, gradientAngle)

        // Step 5: Reconstruct gradient stops from projections
        val stops = reconstructGradientStops(projectedSamples)

        return if (stops.size >= 2) {
            LinearGradient(gradientAngle, stops)
        } else {
            // println("Could not extract enough gradient stops")
            null
        }
    }

    private fun collectSamplesForAngleDetection(): List<ColorSample> {
        val samples = mutableListOf<ColorSample>()

        // Grid sampling for angle detection - use fewer but well-distributed points
        val gridSize = 20 // 20x20 grid
        val stepX = width / gridSize
        val stepY = height / gridSize

        for (gy in 0 until gridSize) {
            for (gx in 0 until gridSize) {
                val x = gx * stepX + stepX / 2
                val y = gy * stepY + stepY / 2

                if (x < width && y < height) {
                    val color = getPixelColor(x, y)
                    if (color != null && color.alpha > transparencyThreshold) {
                        val rgbColor = Color(color.red, color.green, color.blue)
                        samples.add(ColorSample(x, y, rgbColor, getLabColor(rgbColor)))
                    }
                }
            }
        }

        return samples
    }

    private fun detectGradientAngle(samples: List<ColorSample>): Double {
        // Use multiple methods to find the gradient angle and combine results

        // Method 1: Color difference vectors
        val angleVotesFromDiffs = detectAngleByColorDifferences(samples)

        // Method 2: Find the axis with maximum color variance
        val angleFromVariance = detectAngleByMaxVariance(samples)

        // Method 3: Edge detection approach
        val angleFromEdges = detectAngleByEdges(samples)

        // Combine the results (weighted voting)
        val combinedAngleVotes = mutableMapOf<Int, Double>()

        angleVotesFromDiffs.forEach { (angle, weight) ->
            val quantized = quantizeAngle(angle)
            combinedAngleVotes[quantized] = combinedAngleVotes.getOrDefault(quantized, 0.0) + weight * 2.0
        }

        val varianceQuantized = quantizeAngle((angleFromVariance * 180 / PI).toInt())
        combinedAngleVotes[varianceQuantized] = combinedAngleVotes.getOrDefault(varianceQuantized, 0.0) + 1.5

        val edgeQuantized = quantizeAngle((angleFromEdges * 180 / PI).toInt())
        combinedAngleVotes[edgeQuantized] = combinedAngleVotes.getOrDefault(edgeQuantized, 0.0) + 1.0

        // Find the winning angle
        val bestAngle = combinedAngleVotes.maxByOrNull { it.value }?.key ?: 0
        return bestAngle * PI / 180.0
    }

    private fun detectAngleByColorDifferences(samples: List<ColorSample>): Map<Int, Double> {
        val angleVotes = mutableMapOf<Int, Double>()

        // Sample pairs efficiently
        val numPairs = minOf(2000, samples.size * samples.size / 4)

        repeat(numPairs) {
            val i = (Math.random() * samples.size).toInt()
            val j = (Math.random() * samples.size).toInt()

            if (i != j) {
                val s1 = samples[i]
                val s2 = samples[j]

                val dx = s2.x - s1.x
                val dy = s2.y - s1.y
                val distance = sqrt(dx * dx + dy * dy.toDouble())

                if (distance > 20) {
                    val deltaE = s1.lab.deltaE76(s2.lab)

                    if (deltaE > 0.5) { // Even tiny differences matter for subtle gradients
                        val angle = atan2(dy.toDouble(), dx.toDouble())
                        val angleDegrees = (angle * 180 / PI).toInt()
                        val normalizedAngle = normalizeAngle(angleDegrees)

                        // Weight by color difference per unit distance
                        val weight = deltaE / sqrt(distance)
                        angleVotes[normalizedAngle] = angleVotes.getOrDefault(normalizedAngle, 0.0) + weight
                    }
                }
            }
        }

        return angleVotes
    }

    private fun detectAngleByMaxVariance(samples: List<ColorSample>): Double {
        var maxVariance = 0.0
        var bestAngle = 0.0

        // Test angles from 0 to 180 degrees
        for (angleDeg in 0 until 180 step 5) {
            val angle = angleDeg * PI / 180.0
            val dx = cos(angle)
            val dy = sin(angle)

            // Project samples onto this axis
            val projections = samples.map { sample ->
                val projection = (sample.x - centerX) * dx + (sample.y - centerY) * dy
                projection to sample.lab
            }

            // Calculate color variance along this axis
            val variance = calculateColorVariance(projections)

            if (variance > maxVariance) {
                maxVariance = variance
                bestAngle = angle
            }
        }

        return bestAngle
    }

    private fun detectAngleByEdges(samples: List<ColorSample>): Double {
        // Group samples into spatial buckets and find color transitions
        val bucketSize = 50
        val buckets = mutableMapOf<Pair<Int, Int>, MutableList<ColorSample>>()

        samples.forEach { sample ->
            val bucketX = sample.x / bucketSize
            val bucketY = sample.y / bucketSize
            buckets.getOrPut(bucketX to bucketY) { mutableListOf() }.add(sample)
        }

        val angleVotes = mutableMapOf<Int, Double>()

        // Compare adjacent buckets
        buckets.forEach { (coord, bucket1) ->
            val (bx, by) = coord

            // Check all 8 neighbors
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx != 0 || dy != 0) {
                        val neighborCoord = (bx + dx) to (by + dy)
                        buckets[neighborCoord]?.let { bucket2 ->
                            if (bucket1.isNotEmpty() && bucket2.isNotEmpty()) {
                                val avgColor1 = averageLabColor(bucket1.map { it.lab })
                                val avgColor2 = averageLabColor(bucket2.map { it.lab })
                                val deltaE = avgColor1.deltaE76(avgColor2)

                                if (deltaE > 0.5) {
                                    val angle = atan2(dy.toDouble(), dx.toDouble())
                                    val angleDegrees = normalizeAngle((angle * 180 / PI).toInt())
                                    angleVotes[angleDegrees] = angleVotes.getOrDefault(angleDegrees, 0.0) + deltaE
                                }
                            }
                        }
                    }
                }
            }
        }

        val bestAngle = angleVotes.maxByOrNull { it.value }?.key ?: 0
        return bestAngle * PI / 180.0
    }

    private fun collectImageSamples(): List<ColorSample> {
        val samples = mutableListOf<ColorSample>()
        val totalPixels = width * height
        val targetSamples = (totalPixels * targetSampleRate).toInt()

        // Calculate step size to achieve target sample rate
        val step = sqrt(totalPixels.toDouble() / targetSamples).toInt().coerceAtLeast(1)

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = getPixelColor(x, y)
                if (color != null && color.alpha > transparencyThreshold) {
                    val rgbColor = Color(color.red, color.green, color.blue)
                    samples.add(ColorSample(x, y, rgbColor, getLabColor(rgbColor)))
                }
            }
        }

        return samples
    }

    private fun projectSamplesOntoGradientAxis(
        samples: List<ColorSample>,
        gradientAngle: Double
    ): List<Pair<Float, Color>> {
        // The gradient direction vector
        val dx = cos(gradientAngle)
        val dy = sin(gradientAngle)

        // Find the extent of the gradient
        var minProjection = Double.MAX_VALUE
        var maxProjection = Double.MIN_VALUE

        val projections = samples.map { sample ->
            // Project point onto gradient axis (perpendicular distance doesn't matter)
            val projection = (sample.x - centerX) * dx + (sample.y - centerY) * dy
            minProjection = minOf(minProjection, projection)
            maxProjection = maxOf(maxProjection, projection)
            projection to sample.color
        }

        // Normalize projections to 0-1 range
        val range = maxProjection - minProjection
        return if (range > 0) {
            projections.map { (projection, color) ->
                ((projection - minProjection) / range).toFloat() to color
            }
        } else {
            emptyList()
        }
    }

    private fun reconstructGradientStops(projectedSamples: List<Pair<Float, Color>>): List<GradientStop> {
        if (projectedSamples.isEmpty()) return emptyList()

        // Sort by position
        val sorted = projectedSamples.sortedBy { it.first }

        // Group into bins along the gradient
        val numBins = 20
        val binSize = 1.0f / numBins
        val bins = Array(numBins) { mutableListOf<Color>() }

        sorted.forEach { (position, color) ->
            val binIndex = (position / binSize).toInt().coerceIn(0, numBins - 1)
            bins[binIndex].add(color)
        }

        // Create stops from bins with samples
        val stops = mutableListOf<GradientStop>()

        bins.forEachIndexed { index, colors ->
            if (colors.isNotEmpty()) {
                val position = (index + 0.5f) * binSize

                // Use median color instead of average for better noise resistance
                val medianColor = if (colors.size == 1) {
                    colors[0]
                } else {
                    // Sort by luminance and pick middle
                    val sortedColors = colors.sortedBy {
                        0.299 * it.red + 0.587 * it.green + 0.114 * it.blue
                    }
                    sortedColors[sortedColors.size / 2]
                }

                stops.add(GradientStop(position, medianColor))
            }
        }

        // Smooth the gradient stops
        val smoothedStops = smoothGradientStops(stops)

        // Ensure we have start and end stops
        val finalStops = mutableListOf<GradientStop>()

        if (smoothedStops.isNotEmpty()) {
            // Always add 0 and 1 positions
            finalStops.add(GradientStop(0f, smoothedStops.first().color))

            smoothedStops.forEach { stop ->
                if (stop.position > 0.05f && stop.position < 0.95f) {
                    finalStops.add(stop)
                }
            }

            finalStops.add(GradientStop(1f, smoothedStops.last().color))
        }

        return mergeSimilarStops(finalStops)
    }

    private fun smoothGradientStops(stops: List<GradientStop>): List<GradientStop> {
        if (stops.size < 3) return stops

        return stops.mapIndexed { index, stop ->
            when (index) {
                0, stops.size - 1 -> stop
                else -> {
                    // Average with neighbors
                    val prev = stops[index - 1]
                    val next = stops[index + 1]

                    val avgRed = (prev.color.red + stop.color.red * 2 + next.color.red) / 4
                    val avgGreen = (prev.color.green + stop.color.green * 2 + next.color.green) / 4
                    val avgBlue = (prev.color.blue + stop.color.blue * 2 + next.color.blue) / 4

                    GradientStop(
                        stop.position,
                        Color(avgRed.coerceIn(0, 255), avgGreen.coerceIn(0, 255), avgBlue.coerceIn(0, 255))
                    )
                }
            }
        }
    }

    private fun mergeSimilarStops(stops: List<GradientStop>): List<GradientStop> {
        if (stops.size <= 2) return stops

        val merged = mutableListOf<GradientStop>()
        merged.add(stops[0]) // Always keep start

        for (i in 1 until stops.size - 1) {
            val prev = merged.last()
            val curr = stops[i]

            val deltaE = getLabColor(prev.color).deltaE76(getLabColor(curr.color))
            val posDiff = curr.position - prev.position

            // Keep stop if color is different enough or position is far enough
            if (deltaE > 3.0 || posDiff > 0.15f) {
                merged.add(curr)
            }
        }

        merged.add(stops.last()) // Always keep end

        return merged
    }

    private fun calculateColorVariance(projections: List<Pair<Double, LabColor>>): Double {
        if (projections.isEmpty()) return 0.0

        // Group by position buckets
        val buckets = mutableMapOf<Int, MutableList<LabColor>>()
        val numBuckets = 10

        val minProj = projections.minOf { it.first }
        val maxProj = projections.maxOf { it.first }
        val range = maxProj - minProj

        if (range == 0.0) return 0.0

        projections.forEach { (proj, lab) ->
            val bucket = ((proj - minProj) / range * numBuckets).toInt().coerceIn(0, numBuckets - 1)
            buckets.getOrPut(bucket) { mutableListOf() }.add(lab)
        }

        // Calculate variance between buckets
        var totalVariance = 0.0
        val bucketAverages = buckets.mapValues { (_, labs) -> averageLabColor(labs) }

        bucketAverages.values.forEach { lab1 ->
            bucketAverages.values.forEach { lab2 ->
                totalVariance += lab1.deltaE76(lab2)
            }
        }

        return totalVariance
    }

    private fun averageLabColor(labs: List<LabColor>): LabColor {
        if (labs.isEmpty()) return LabColor(0.0, 0.0, 0.0)

        val avgL = labs.sumOf { it.l } / labs.size
        val avgA = labs.sumOf { it.a } / labs.size
        val avgB = labs.sumOf { it.b } / labs.size

        return LabColor(avgL, avgA, avgB)
    }

    private fun normalizeAngle(degrees: Int): Int {
        // Normalize to 0-179 range (180 and 0 are the same)
        return ((degrees % 180) + 180) % 180
    }

    private fun quantizeAngle(degrees: Int, binSize: Int = 5): Int {
        // Quantize to nearest bin
        val normalized = normalizeAngle(degrees)
        return (normalized / binSize) * binSize
    }

    private fun getPixelColor(x: Int, y: Int): Color? {
        if (x < 0 || x >= width || y < 0 || y >= height) return null

        return try {
            Color(image.getRGB(x, y), true)
        } catch (e: Exception) {
            null
        }
    }

    private fun getLabColor(color: Color): LabColor {
        return labCache.computeIfAbsent(color) {
            LabColor.fromRgb(color)
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}