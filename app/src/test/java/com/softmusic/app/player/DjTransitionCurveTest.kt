package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjTransitionCurveTest {
    @Test
    fun everyCurveStartsOnOutgoingAndFinishesOnIncoming() {
        curves.forEach { curve ->
            val start = curve.gainsAt(-1f, 0.70f)
            val end = curve.gainsAt(2f, 0.70f)
            assertEquals(1f, start.outgoing, 0.00001f)
            assertEquals(0f, start.incoming, 0f)
            assertEquals(0f, end.outgoing, 0.00001f)
            assertEquals(1f, end.incoming, 0.00001f)
        }
    }

    @Test
    fun classicMaintainsEqualPowerAcrossTheWholeFade() {
        for (step in 0..24) {
            val gains = DjTransitionCurve.Classic.gainsAt(step / 24f)
            assertEquals(1f, gains.outgoing * gains.outgoing + gains.incoming * gains.incoming, 0.00001f)
        }
    }

    @Test
    fun expertHeadroomIsLowestAtTheCenterAndLevelMatchRecoversAtTheEnd() {
        curves.drop(1).forEach { curve ->
            assertEquals(1f, curve.headroomAt(0f), 0.00001f)
            assertEquals(curve.mixHeadroom, curve.headroomAt(0.5f), 0.00001f)
            assertEquals(1f, curve.headroomAt(1f), 0.00001f)
            val matched = curve.gainsAt(0.5f, 0.70f)
            val unmatched = curve.gainsAt(0.5f)
            assertEquals(unmatched.outgoing, matched.outgoing, 0f)
            assertTrue(matched.incoming < unmatched.incoming)
            assertEquals(1f, curve.gainsAt(1f, 0.70f).incoming, 0.00001f)
        }
    }

    @Test
    fun transitionCompensationRecoversSmoothlyWithoutChangingOutgoingGain() {
        curves.drop(1).forEach { curve ->
            listOf(0.25f to 0.49375f, 0.5f to 0.7f, 0.75f to 0.90625f).forEach { (progress, level) ->
                val compensated = curve.gainsAt(progress, 0.70f, incomingLevelGain = 0.4f)
                val uncompensated = curve.gainsAt(progress, 0.70f)
                assertEquals(uncompensated.outgoing, compensated.outgoing, 0f)
                assertEquals(uncompensated.incoming * level, compensated.incoming, 0.00001f)
            }
        }
    }

    @Test
    fun compensatedIncomingGainRisesThroughoutFadeAndFinishesExactlyAtUnity() {
        curves.forEach { curve ->
            listOf(0.70f, 1f).forEach { startScale ->
                listOf(0.4f, 0.7f, 1f).forEach { levelGain ->
                    var previousIncoming = 0f
                    for (step in 0..1_000) {
                        val gains = curve.gainsAt(step / 1_000f, startScale, levelGain)
                        assertTrue("Incoming gain fell at step $step for $curve / $startScale / $levelGain",
                            gains.incoming >= previousIncoming)
                        assertTrue(gains.incoming in 0f..1f)
                        previousIncoming = gains.incoming
                    }
                    val end = curve.gainsAt(1f, startScale, levelGain)
                    assertEquals(1f, end.incoming, 0f)
                    assertEquals(0f, end.outgoing, 0f)
                    assertEquals(end, curve.gainsAt(1.2f, startScale, levelGain))
                }
            }
        }
    }

    @Test
    fun invalidCompensationUsesUnityAndExcessiveAttenuationIsBounded() {
        for (step in 0..24) {
            val progress = step / 24f
            val curve = DjTransitionCurve.ExpertNormal
            val uncompensated = curve.gainsAt(progress)
            assertEquals(uncompensated, curve.gainsAt(progress, incomingLevelGain = Float.NaN))
            assertEquals(uncompensated, curve.gainsAt(progress, incomingLevelGain = Float.POSITIVE_INFINITY))
            assertEquals(uncompensated, curve.gainsAt(progress, incomingLevelGain = 2f))
            assertEquals(curve.gainsAt(progress, incomingLevelGain = 0.4f),
                curve.gainsAt(progress, incomingLevelGain = 0f))
        }
    }

    private val curves = listOf(
        DjTransitionCurve.Classic,
        DjTransitionCurve.ExpertNormal,
        DjTransitionCurve.ExpertSilence,
        DjTransitionCurve.ExpertSilenceStrongIncoming,
        DjTransitionCurve.ExpertStrongIncoming,
    )
}
