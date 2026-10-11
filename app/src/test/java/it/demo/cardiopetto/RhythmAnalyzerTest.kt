package it.demo.cardiopetto

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RhythmAnalyzerTest {
    private fun times(irregular: Boolean=false): List<Double> {
        var t=0.5
        val result=mutableListOf(t)
        repeat(23) { i -> t+=when { irregular && i==11 -> 0.5; irregular && i==12 -> 1.1; else -> 0.8 }; result.add(t) }
        return result
    }
    private fun signal(pulses: List<Double> = times()) = (0..1000).map { i ->
        val t=i*0.02
        RhythmAnalyzer.Sample(t,0.0001+pulses.sumOf { 0.01*exp(-((t-it)/0.04).pow(2)/2) })
    }
    @Test fun regularSignalHasNoEvents() {
        val a=RhythmAnalyzer.analyze(signal())
        assertTrue(a.reason,a.usable);assertEquals(75,a.bpm);assertTrue(a.events.isEmpty())
    }
    @Test fun injectedShortLongPairIsLocatedWithoutDiagnosis() {
        val a=RhythmAnalyzer.analyze(signal(times(true)))
        assertTrue(a.reason,a.usable);assertEquals(1,a.events.size)
        assertEquals(500.0,a.events.single().shortMs.toDouble(),25.0);assertEquals(1100.0,a.events.single().longMs.toDouble(),25.0)
        assertEquals(9.8,a.events.single().seconds,0.03)
    }
    @Test fun missingPulseDoesNotBecomePrematureBeat() {
        val a=RhythmAnalyzer.analyze(signal(times().filterIndexed { i,_ -> i != 12 }))
        assertTrue(a.events.isEmpty())
    }
    @Test fun flatSignalRejected() {
        val a=RhythmAnalyzer.analyze((0..1000).map { RhythmAnalyzer.Sample(it*0.02,0.001) })
        assertFalse(a.usable);assertNull(a.bpm);assertTrue(a.events.isEmpty())
    }
    @Test fun movementSuppressesEvenInjectedEvent() {
        val s=signal(times(true)).mapIndexed { i,x -> if(i==300) x.copy(motion=true) else x }
        val a=RhythmAnalyzer.analyze(s);assertFalse(a.usable);assertTrue(a.events.isEmpty())
    }
    @Test fun gapSuppressesEvenInjectedEvent() {
        val a=RhythmAnalyzer.analyze(signal(times(true)).filter { it.seconds !in 4.0..4.3 })
        assertFalse(a.usable);assertTrue(a.events.isEmpty())
    }
    @Test fun timestampReversalRejected() {
        val s=signal().toMutableList();s[100]=s[100].copy(seconds=0.0)
        assertFalse(RhythmAnalyzer.analyze(s).usable)
    }
    @Test fun lowSampleRateRejected() { assertFalse(RhythmAnalyzer.analyze(signal().filterIndexed { i,_ -> i%2==0 }).usable) }
    @Test fun nonFiniteInputRejected() {
        val s=signal().toMutableList();s[400]=s[400].copy(amplitude=Double.NaN)
        assertFalse(RhythmAnalyzer.analyze(s).usable)
    }
    @Test fun tooShortRecordingRejected() { assertFalse(RhythmAnalyzer.analyze(signal().take(450)).usable) }
    @Test fun largeSpikeRejected() {
        val s=signal().toMutableList();s[400]=s[400].copy(amplitude=10.0)
        assertFalse(RhythmAnalyzer.analyze(s).usable)
    }
}
