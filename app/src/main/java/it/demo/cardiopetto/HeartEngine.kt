package it.demo.cardiopetto

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

/** A signal-quality-gated experimental mechanical-pulse estimator, NOT an ECG. */
class HeartEngine(private val sensors: SensorManager) : SensorEventListener {
    data class Result(val bpm: Int?, val quality: Double, val seconds: Double, val sensor: String,
                      val waveform: List<Float>, val samples: Int)

    private val chosen = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available get() = chosen != null
    val sensorName get() = if (chosen?.type == Sensor.TYPE_GYROSCOPE) "Giroscopio" else "Accelerometro"
    private var recording = false
    private var firstTimestamp = 0L
    private var lastTimestamp = 0L
    private var nextBin = 0L
    private val baseline = DoubleArray(3)
    private var envelope = 0.0
    private val bins = ArrayDeque<Double>()
    private val raw = StringBuilder()
    private var n = 0
    private val stepNs = 20_000_000L  // 50 Hz uniform binning; sample rate actually depends on hardware.

    fun start(): Boolean {
        if (chosen == null) return false
        reset()
        recording = sensors.registerListener(this, chosen, 10_000) // request ~100 Hz
        return recording
    }
    fun stop(): String {
        recording = false
        sensors.unregisterListener(this)
        return raw.toString()
    }
    private fun reset() {
        firstTimestamp = 0; lastTimestamp = 0; nextBin = 0
        baseline.fill(0.0); envelope = 0.0; bins.clear(); n = 0
        raw.setLength(0)
        raw.append("seconds,sensor,x,y,z,envelope\n")
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        if (!recording || event.sensor.type != chosen?.type) return
        val time = event.timestamp
        if (firstTimestamp == 0L) {
            firstTimestamp = time; lastTimestamp = time; nextBin = time
            for (a in 0..2) baseline[a] = event.values[a].toDouble()
            return
        }
        val dt = ((time - lastTimestamp) / 1e9).coerceIn(0.001, 0.10)
        lastTimestamp = time
        val lp = exp(-dt / 0.25) // remove drift/gravity/very slow movement
        var energy = 0.0
        for (a in 0..2) {
            val x = event.values[a].toDouble()
            baseline[a] = lp * baseline[a] + (1 - lp) * x
            energy += (x - baseline[a]).pow(2)
        }
        val amplitude = sqrt(energy)
        val sm = exp(-dt / 0.045)
        envelope = sm * envelope + (1 - sm) * amplitude
        if (raw.length < 3_000_000) {
            raw.append("%.4f,%s,%.6f,%.6f,%.6f,%.7f\n".format(
                java.util.Locale.US, (time-firstTimestamp)/1e9, sensorName,
                event.values[0], event.values[1], event.values[2], envelope))
        }
        n++
        // Do not fill gaps longer than 250 ms with fake samples.
        if (time - nextBin > 250_000_000L) nextBin = time
        while (time >= nextBin) {
            bins.addLast(envelope)
            if (bins.size > 900) bins.removeFirst() // last 18 seconds
            nextBin += stepNs
        }
    }

    fun snapshot(): Result {
        val data = bins.toList()
        val duration = if (firstTimestamp == 0L) 0.0 else (lastTimestamp-firstTimestamp)/1e9
        val waveform = data.takeLast(350).map { it.toFloat() }
        if (data.size < 400) return Result(null, 0.0, duration, sensorName, waveform, n)
        // Correlation of the recent 12 s of detrended amplitude envelope.
        // Motion/respiration can produce false harmonics: threshold is deliberately conservative.
        val x = data.takeLast(600).toDoubleArray()
        val mean = x.average()
        for (i in x.indices) x[i] -= mean
        val variance = x.map { it * it }.average()
        if (variance < 1e-12) return Result(null, 0.0, duration, sensorName, waveform, n)
        var best = -1.0
        var bestLag = 0
        for (lag in 23..75) {  // 40-130 bpm, 50 Hz envelope
            var dot = 0.0; var a = 0.0; var b = 0.0
            for (i in lag until x.size) {
                dot += x[i] * x[i-lag]
                a += x[i]*x[i]; b += x[i-lag]*x[i-lag]
            }
            val r = dot / sqrt(a*b + 1e-16)
            if (r > best) { best = r; bestLag = lag }
        }
        val confidence = best.coerceIn(0.0, 1.0)
        val bpm = if (confidence >= 0.55) (3000.0 / bestLag).roundToInt() else null
        return Result(bpm, confidence, duration, sensorName, waveform, n)
    }
}
