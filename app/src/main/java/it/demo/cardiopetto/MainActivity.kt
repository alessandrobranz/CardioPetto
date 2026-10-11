package it.demo.cardiopetto

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private lateinit var engine: HeartEngine
    private lateinit var bpm: TextView
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var events: TextView
    private lateinit var control: Button
    private lateinit var marker: Button
    private lateinit var graph: SignalView
    private val handler=Handler(Looper.getMainLooper())
    private var running=false
    private var startedAt=0L
    private var lastSampleCount=0
    private var lastProgressAt=0L
    private var savedFile: File?=null
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private val update=object: Runnable {
        override fun run() {
            if(!running) return
            val r=engine.snapshot()
            render(r)
            val now=SystemClock.elapsedRealtime()
            if(r.samples != lastSampleCount) { lastSampleCount=r.samples;lastProgressAt=now }
            when {
                now-lastProgressAt > 5000 -> stopRecording("Acquisizione interrotta: il sensore non invia dati")
                now-startedAt >= 120_000 -> stopRecording()
                else -> handler.postDelayed(this,500)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine=HeartEngine(getSystemService(SENSOR_SERVICE) as SensorManager)
        savedFile=File(filesDir,"sessions").listFiles()?.filter { it.extension=="zip" }?.maxByOrNull { it.lastModified() }
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(24))
            setBackgroundColor(Color.rgb(247,248,252))
        }
        fun text(size: Float, value: String): TextView = TextView(this).apply {
            textSize=size;text=value;setTextColor(Color.rgb(30,38,62));setPadding(0,dp(6),0,dp(6))
            root.addView(this,LinearLayout.LayoutParams(-1,-2))
        }
        text(28f,"CardioPetto · 0.2")
        text(15f,"Irregolarità del segnale · sperimentale")
        bpm=text(38f,"— BPM")
        status=text(17f,"Pronto per una registrazione di 2 minuti")
        graph=SignalView();root.addView(graph,LinearLayout.LayoutParams(-1,dp(130)))
        details=text(14f,"${engine.sensorName} · telefono fermo sul torace")
        events=text(16f,"Nessuna sessione avviata")
        control=Button(this).apply {
            text="AVVIA REGISTRAZIONE";setOnClickListener { if(running) stopRecording() else startRecording() }
        };root.addView(control)
        marker=Button(this).apply {
            text="HO AVVERTITO UN EPISODIO";isEnabled=false
            setOnClickListener { engine.mark();text="EPISODIO ANNOTATO (${engine.markers.size})" }
        };root.addView(marker)
        root.addView(Button(this).apply {
            text="CONDIVIDI ULTIMA SESSIONE"
            setOnClickListener { if(running) stopRecording();share(savedFile) }
        })
        root.addView(Button(this).apply { text="SESSIONI SALVATE";setOnClickListener { showSessions() } })
        text(14f,"L'app cerca sequenze breve-lungo tra pulsazioni meccaniche candidate. Non identifica extrasistoli e non calcola la probabilità di averle. Zero eventi non esclude aritmie.")
        text(14f,"Sdraiati supino, telefono sul torace sopra un indumento sottile. Rimani fermo e respira normalmente. Toccare il pulsante sintomo può disturbare la misura. Le prime valutazioni richiedono almeno 12 secondi.")
        text(14f,"Se gli episodi si ripetono, parlane con il medico per valutare ECG/Holter. Dolore toracico, svenimento o forte difficoltà respiratoria richiedono assistenza urgente: 112. Non aspettare l'esito dell'app.")
        val scroll=ScrollView(this).apply { addView(root) }
        // Keep controls clear of status/navigation bars with target SDK 35 edge-to-edge.
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets
        }
        setContentView(scroll)
        if(!engine.available) { control.isEnabled=false;status.text="Sensore compatibile non disponibile" }
    }
    private fun startRecording() {
        if(!engine.start()) { status.text="Impossibile avviare il sensore";return }
        running=true;savedFile=null;startedAt=SystemClock.elapsedRealtime();lastProgressAt=startedAt;lastSampleCount=0
        marker.isEnabled=true;marker.text="HO AVVERTITO UN EPISODIO"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        control.text="TERMINA E SALVA";handler.post(update)
    }
    private fun render(r: HeartEngine.Result) {
        bpm.text=r.analysis.bpm?.let { "$it BPM · stima" } ?: "— BPM"
        status.text=if(r.analysis.usable) "Segnale analizzabile · nessuna diagnosi" else r.analysis.reason
        details.text="${engine.sensorName} · ${r.seconds.toInt()} s · ${r.samples} campioni · ${r.markers} annotazioni"
        graph.points=r.waveform;graph.invalidate()
        events.text=if(r.events.isEmpty()) "0 eventi breve-lungo rilevati finora. Non esclude extrasistoli." else {
            val last=r.events.last()
            "${r.events.size} eventi sospetti del segnale\nUltimo a ${last.seconds.toInt()} s: ${last.shortMs} / ${last.longMs} ms. Possibili artefatti; da verificare con ECG."
        }
    }
    private fun stopRecording(reason: String?=null) {
        if(!running) return
        running=false;handler.removeCallbacks(update);engine.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        marker.isEnabled=false;control.text="AVVIA REGISTRAZIONE"
        val r=engine.snapshot();render(r)
        try { savedFile=SessionExport.save(this,engine,r);status.text=(reason?.plus(". ") ?: "")+"Sessione salvata sul telefono" }
        catch(e: Exception) { status.text="Salvataggio non riuscito: ${e.localizedMessage}" }
    }
    private fun showSessions() {
        val files=File(filesDir,"sessions").listFiles()?.filter { it.extension=="zip" }?.sortedByDescending { it.lastModified() }.orEmpty()
        if(files.isEmpty()) { status.text="Nessuna sessione salvata";return }
        AlertDialog.Builder(this).setTitle("Scegli una sessione da condividere")
            .setItems(files.map { it.name }.toTypedArray()) { _,i -> share(files[i]) }.setNegativeButton("Chiudi",null).show()
    }
    private fun share(file: File?) {
        if(file==null || !file.exists()) { status.text="Registra prima una sessione";return }
        val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="application/zip";putExtra(Intent.EXTRA_STREAM,uri)
            clipData=ClipData.newRawUri("Sessione CardioPetto",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent,"Condividi dati della sessione"))
    }
    override fun onPause() { if(running) stopRecording("App in pausa");super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(update);engine.stop();super.onDestroy() }
    private inner class SignalView: View(this@MainActivity) {
        var points: List<Float> = emptyList()
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(190,65,90);strokeWidth=dp(2).toFloat() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas);canvas.drawColor(Color.WHITE)
            if(points.size<2) return
            val min=points.minOrNull() ?: return;val max=points.maxOrNull() ?: return
            val span=(max-min).coerceAtLeast(1e-7f)
            for(i in 1 until points.size) canvas.drawLine((i-1)*width.toFloat()/(points.size-1),height*.9f-(points[i-1]-min)/span*height*.8f,
                i*width.toFloat()/(points.size-1),height*.9f-(points[i]-min)/span*height*.8f,paint)
        }
    }
}
