package it.demo.cardiopetto

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var engine: HeartEngine
    private lateinit var bpm: TextView
    private lateinit var details: TextView
    private lateinit var status: TextView
    private lateinit var control: Button
    private lateinit var graph: SignalView
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var savedFile: File? = null
    private val update = object : Runnable {
        override fun run() {
            if (!running) return
            val result = engine.snapshot()
            bpm.text = result.bpm?.let { "$it BPM" } ?: "— BPM"
            status.text = when {
                result.seconds < 8 -> "Acquisizione iniziale: rimani immobile"
                result.bpm == null -> "Segnale incerto: riposiziona il telefono"
                else -> "Stima sperimentale disponibile"
            }
            details.text = "${result.sensor}  ·  ${"%.0f".format(result.seconds)} s  ·  " +
                "qualità ${"%.0f".format(result.quality*100)}%  ·  ${result.samples} campioni"
            graph.points = result.waveform
            graph.invalidate()
            if (result.seconds >= 120) stopRecording() else handler.postDelayed(this, 350)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = HeartEngine(getSystemService(SENSOR_SERVICE) as SensorManager)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
            setBackgroundColor(Color.rgb(248, 249, 252))
        }
        fun label(size: Float, color: Int, text: String): TextView = TextView(this).apply {
            textSize = size; setTextColor(color); this.text = text
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = label(25f, Color.rgb(30, 38, 62), "CardioPetto")
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(label(13f, Color.DKGRAY, "Studio del battito con i sensori del telefono"))
        bpm = label(52f, Color.rgb(195, 40, 77), "— BPM")
        root.addView(bpm, LinearLayout.LayoutParams(-1, 115))
        status = label(16f, Color.DKGRAY, "Appoggia il telefono sul petto e resta fermo")
        root.addView(status)
        graph = SignalView()
        root.addView(graph, LinearLayout.LayoutParams(-1, 200))
        details = label(13f, Color.DKGRAY, "Sensore: ${engine.sensorName}")
        root.addView(details)
        control = Button(this).apply {
            text = "AVVIA MISURAZIONE"
            setOnClickListener { if (running) stopRecording() else startRecording() }
        }
        root.addView(control)
        val export = Button(this).apply {
            text = "CONDIVIDI CSV ULTIMA SESSIONE"
            setOnClickListener {
                val f = savedFile
                if (f == null || !f.exists()) {
                    status.text = "Effettua prima una registrazione"
                } else {
                    // Share via ACTION_SEND with text data, avoiding external file permissions.
                    // Restricted to the first 1 MB to avoid binder size exceptions.
                    val content = f.readText().take(650_000)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, f.name)
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    startActivity(Intent.createChooser(intent, "Condividi dati sperimentali"))
                }
            }
        }
        root.addView(export)
        val guidance = label(13f, Color.rgb(90, 96, 104),
            "Sdraiati supino. Appoggia lo smartphone fermo sul torace (sopra un indumento sottile). " +
            "Mantieni lo schermo acceso. Misura per almeno 20–30 secondi. " +
            "Non è un ECG, non riconosce malattie né sostituisce dispositivi medici.")
        guidance.setPadding(0, 22, 0, 0)
        root.addView(guidance)
        if (!engine.available) {
            control.isEnabled = false
            status.text = "Questo dispositivo non ha sensori di movimento compatibili"
        }
        setContentView(root)
    }
    private fun startRecording() {
        savedFile = null
        if (!engine.start()) { status.text = "Impossibile avviare il sensore"; return }
        running = true
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        control.text = "TERMINA E SALVA"
        handler.post(update)
    }
    private fun stopRecording() {
        if (!running) return
        running = false
        handler.removeCallbacks(update)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val csv = engine.stop()
        val filename = "cardiopetto_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        val directory = getExternalFilesDir(null) ?: filesDir
        try {
            savedFile = File(directory, filename).also { it.writeText(csv) }
            status.text = "Sessione salvata: $filename"
        } catch (e: Exception) {
            status.text = "Errore nel salvataggio: ${e.localizedMessage}"
        }
        control.text = "AVVIA MISURAZIONE"
    }
    override fun onPause() {
        if (running) stopRecording()
        super.onPause()
    }

    private inner class SignalView : View(this@MainActivity) {
        var points: List<Float> = emptyList()
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(211, 57, 93); strokeWidth = 3f; style = Paint.Style.STROKE
        }
        private val background = Paint().apply { color = Color.WHITE }
        private val grid = Paint().apply { color = Color.rgb(230, 233, 239); strokeWidth = 1f }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawRoundRect(0f, 12f, width.toFloat(), height.toFloat()-8, 16f, 16f, background)
            for (j in 1..3) c.drawLine(0f, height*j/4f, width.toFloat(), height*j/4f, grid)
            if (points.size < 2) return
            val min = points.minOrNull() ?: return
            val max = points.maxOrNull() ?: return
            val range = (max-min).coerceAtLeast(0.0000001f)
            for (i in 1 until points.size) {
                val x1 = (i-1)*width.toFloat()/(points.size-1)
                val x2 = i*width.toFloat()/(points.size-1)
                val y1 = height*.85f - (points[i-1]-min)/range*height*.70f
                val y2 = height*.85f - (points[i]-min)/range*height*.70f
                c.drawLine(x1, y1, x2, y2, line)
            }
        }
    }
}
