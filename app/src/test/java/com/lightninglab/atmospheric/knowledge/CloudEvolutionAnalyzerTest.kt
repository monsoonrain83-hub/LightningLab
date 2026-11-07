package com.lightninglab.atmospheric.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEvolutionAnalyzerTest {
    private val analyzer = CloudEvolutionAnalyzer()

    @Test fun detectsIncreasingVisibleGrowth() {
        val report = analyzer.analyze(listOf(
            CloudFrameObservation(0, "CU", growthStage = CloudGrowthStage.SHALLOW),
            CloudFrameObservation(10_000, "CU", growthStage = CloudGrowthStage.MODERATE),
            CloudFrameObservation(20_000, "CB", growthStage = CloudGrowthStage.STRONG, distinctAnvil = true)
        ))
        assertEquals(CloudEvolutionTrend.INCREASING, report.trend)
        assertTrue(report.events.any { it.titlePersian.contains("سندانی") })
        assertEquals(20_000L, report.durationMillis)
    }

    @Test fun detectsPrecipitationOnsetAsVisualEvent() {
        val report = analyzer.analyze(listOf(
            CloudFrameObservation(0, "CB"),
            CloudFrameObservation(5_000, "CB", precipitationStreaks = true),
            CloudFrameObservation(10_000, "CB", precipitationStreaks = true, precipitationReachesGround = true)
        ))
        assertTrue(report.events.any { it.titlePersian == "ظهور رگه‌های بارش" })
        assertTrue(report.events.any { it.titlePersian == "بارش تا سطح زمین" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateTimestamps() {
        analyzer.analyze(listOf(CloudFrameObservation(0), CloudFrameObservation(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresAtLeastTwoObservations() {
        analyzer.analyze(listOf(CloudFrameObservation(0)))
    }
}
