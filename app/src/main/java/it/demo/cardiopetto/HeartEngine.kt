package it.demo.cardiopetto

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

class HeartEngine(private val sensors: SensorManager) : SensorEventListener {
    data class Raw(val seconds: Double, val x: Float, val y: Float, val z: Float, val envelope: Double, val motion: Boolean)
    data class Marker(val seconds: Double)
    data class Result(val seconds: Double, val analysis: RhythmAnalyzer.Analysis, val waveform: List<Float>,
                      val events: List<RhythmAnalyzer.Event>, val samples: Int, val markers: Int)
    private val chosen = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val available get() = chosen != null
    val sensorName get() = when(chosen?.type) { Sensor.TYPE_GYROSCOPE -> "Giroscopio"; Sensor.TYPE_ACCELEROMETER -> "Accelerometro"; else -> "Non disponibile" }
    val raw = mutableListOf<Raw>()
    val markers = mutableListOf<Marker>()
    private val uniform = mutableListOf<RhythmAnalyzer.Sample>()
    private val events = mutableListOf<RhythmAnalyzer.Event>()
    private val baseline = DoubleArray(3)
    private var recording = false
    private var first = 0L
    private var last = 0L
    private var envelope = 0.0
    private var previous: RhythmAnalyzer.Sample? = null
    private var nextBin = 0.0
    private var lastAnalysis = Double.NEGATIVE_INFINITY
    private var result = RhythmAnalyzer.Analysis(false, "In attesa del segnale")
    var analyzedWindows = 0; private set
    var rejectedWindows = 0; private set
    val seconds get() = if(first == 0L) 0.0 else (last-first)/1e9
    fun start(): Boolean {
        if(chosen == null) return false
        raw.clear(); markers.clear(); uniform.clear(); events.clear(); baseline.fill(0.0)
        first=0; last=0; envelope=0.0; previous=null; nextBin=0.0
        lastAnalysis=Double.NEGATIVE_INFINITY; analyzedWindows=0; rejectedWindows=0
        result=RhythmAnalyzer.Analysis(false,"Acquisizione iniziale")
        recording=sensors.registerListener(this,chosen,10_000)
        return recording
    }
    fun stop() { recording=false; sensors.unregisterListener(this); snapshot() }
    fun mark() { if(recording) markers.add(Marker(seconds)) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        if(!recording || event.sensor.type != chosen?.type || raw.size >= 25_000) return
        if(first == 0L) {
            first=event.timestamp;last=first
            for(a in 0..2) baseline[a]=event.values[a].toDouble()
            return
        }
        if(event.timestamp <= last) return
        val actualDt=(event.timestamp-last)/1e9
        last=event.timestamp
        val dt=actualDt.coerceIn(0.001,0.10)
        val lp=exp(-dt/0.25)
        var energy=0.0
        for(a in 0..2) {
            val v=event.values[a].toDouble()
            if(!v.isFinite()) return
            baseline[a]=lp*baseline[a]+(1-lp)*v
            energy+=(v-baseline[a]).pow(2)
        }
        val amplitude=sqrt(energy)
        val motion=amplitude > if(chosen?.type == Sensor.TYPE_GYROSCOPE) 0.35 else 1.5
        val sm=exp(-dt/0.025)
        envelope=sm*envelope+(1-sm)*amplitude
        val now=RhythmAnalyzer.Sample(seconds,envelope,motion)
        raw.add(Raw(seconds,event.values[0],event.values[1],event.values[2],envelope,motion))
        val old=previous
        // Never bridge gaps. The analyzer rejects a window spanning a gap.
        if(old == null || actualDt > 0.06) {
            uniform.add(now); nextBin=seconds+0.02
        } else {
            while(nextBin <= seconds) {
                val fraction=((nextBin-old.seconds)/(seconds-old.seconds)).coerceIn(0.0,1.0)
                uniform.add(RhythmAnalyzer.Sample(nextBin,old.amplitude+fraction*(envelope-old.amplitude),motion||old.motion))
                nextBin+=0.02
            }
        }
        previous=now
    }
    fun snapshot(): Result {
        if(seconds-lastAnalysis >= 1.0) {
            lastAnalysis=seconds
            val window=uniform.filter { it.seconds >= seconds-18.0 }
            val recentRaw=raw.filter { it.seconds >= seconds-18.0 }
            val intervals=recentRaw.zipWithNext { a,b -> b.seconds-a.seconds }.sorted()
            result=if(intervals.isNotEmpty() && (intervals[intervals.size/2] > 0.025 || intervals.last() > 0.06))
                RhythmAnalyzer.Analysis(false,"Campionamento sensore insufficiente o interrotto")
            else RhythmAnalyzer.analyze(window)
            if(seconds >= 12.0) { analyzedWindows++; if(!result.usable) rejectedWindows++ }
            if(result.usable) for(e in result.events) {
                if(events.none { abs(it.seconds-e.seconds)<0.20 }) events.add(e)
            }
        }
        return Result(seconds,result,uniform.takeLast(350).map { it.amplitude.toFloat() },events.toList(),raw.size,markers.size)
    }
}
