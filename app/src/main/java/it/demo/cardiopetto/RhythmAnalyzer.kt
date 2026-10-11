package it.demo.cardiopetto

import kotlin.math.*

/** Experimental signal heuristics. No clinical probability or diagnosis is produced. */
object RhythmAnalyzer {
    const val VERSION = "timing-heuristic-0.2"
    data class Sample(val seconds: Double, val amplitude: Double, val motion: Boolean = false)
    data class Pulse(val seconds: Double, val amplitude: Double)
    data class Event(val seconds: Double, val shortMs: Int, val longMs: Int, val baselineMs: Int)
    data class Analysis(
        val usable: Boolean, val reason: String, val pulses: List<Pulse> = emptyList(),
        val events: List<Event> = emptyList(), val bpm: Int? = null
    )
    private fun median(x: List<Double>): Double = x.sorted().let {
        if (it.isEmpty()) 0.0 else if (it.size % 2 == 0) (it[it.size / 2 - 1] + it[it.size / 2]) / 2 else it[it.size / 2]
    }

    fun analyze(samples: List<Sample>): Analysis {
        fun reject(reason: String) = Analysis(false, reason)
        if (samples.size < 400 || samples.last().seconds - samples.first().seconds < 12.0)
            return reject("Servono almeno 12 secondi di segnale continuo")
        if (samples.any { !it.seconds.isFinite() || !it.amplitude.isFinite() })
            return reject("Dati sensore non validi")
        val steps = samples.zipWithNext { a, b -> b.seconds - a.seconds }
        if (steps.any { it <= 0.0 || it > 0.08 } || median(steps) > 0.025)
            return reject("Campionamento insufficiente o interrotto")
        if (samples.any { it.motion }) return reject("Movimento rilevato: riposiziona e rimani fermo")
        val sorted = samples.map { it.amplitude }.sorted()
        val low = sorted[sorted.size / 5]
        val high = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]
        val span = high - low
        if (span < 1e-7) return reject("Segnale troppo debole")
        // Absolute and relative gates are technical heuristics, not validated sensitivity.
        if (sorted.last() > high + 8 * span) return reject("Artefatto impulsivo nel segnale")
        val threshold = low + 0.42 * span
        val peaks = mutableListOf<Int>()
        for (i in 1 until samples.lastIndex) {
            val a = samples[i]
            if (a.amplitude < threshold || a.amplitude <= samples[i-1].amplitude || a.amplitude < samples[i+1].amplitude) continue
            if (peaks.isEmpty() || a.seconds - samples[peaks.last()].seconds >= 0.28) peaks.add(i)
            else if (a.amplitude > samples[peaks.last()].amplitude) peaks[peaks.lastIndex] = i
        }
        if (peaks.size < 8) return reject("Pulsazioni candidate insufficienti")
        val intervals = peaks.zipWithNext { a, b -> samples[b].seconds - samples[a].seconds }
        val typical = median(intervals)
        if (typical !in (1.0/3.0)..1.5 || intervals.any { it < 0.28 || it > 2.5 })
            return reject("Pulsazioni ambigue: intervalli non interpretabili")
        // Require consistent local morphology to suppress many non-pulse peaks.
        val windows = peaks.filter { it >= 5 && it + 5 <= samples.lastIndex }.map { p ->
            (p-5..p+5).map { samples[it].amplitude }.let { w ->
                val m = w.average(); val norm = sqrt(w.sumOf { (it-m).pow(2) })
                w.map { (it-m) / norm.coerceAtLeast(1e-12) }
            }
        }
        if (windows.size < 6) return reject("Pulsazioni candidate insufficienti")
        val template = (0..10).map { column -> median(windows.map { it[column] }) }
        val norm = sqrt(template.sumOf { it*it }).coerceAtLeast(1e-12)
        val similarities = windows.map { w -> w.indices.sumOf { w[it]*template[it]/norm } }
        if (similarities.count { it >= 0.75 }.toDouble() / similarities.size < 0.8)
            return reject("Forma delle pulsazioni instabile")
        val pulses = peaks.map { Pulse(samples[it].seconds, samples[it].amplitude) }
        val events = mutableListOf<Event>()
        // A short/long pair is only a timing pattern, NOT a diagnosis of ectopy.
        // Use prior intervals, never the candidate pair, to establish the local baseline.
        for (i in 6 until intervals.lastIndex) {
            val prior = intervals.subList((i-8).coerceAtLeast(0), i)
            val baseline = median(prior)
            if (median(prior.map { abs(it-baseline) }) / baseline > 0.12) continue
            val short = intervals[i]; val long = intervals[i+1]
            if (short < 0.78*baseline && long > 1.20*baseline &&
                (short+long)/(2*baseline) in 0.85..1.15) {
                events.add(Event(pulses[i+1].seconds, (short*1000).roundToInt(),
                    (long*1000).roundToInt(), (baseline*1000).roundToInt()))
            }
        }
        return Analysis(true, "Segnale analizzabile sperimentalmente", pulses, events, (60/typical).roundToInt())
    }
}
