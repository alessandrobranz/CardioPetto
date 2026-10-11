package it.demo.cardiopetto

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SessionExport {
    fun save(context: Context, engine: HeartEngine, snapshot: HeartEngine.Result): File {
        val folder=File(context.filesDir,"sessions").apply { mkdirs() }
        val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
        val file=File(folder,"CardioPetto_$stamp.zip")
        val temporary=File(folder,"$stamp.tmp")
        val raw=buildString {
            append("seconds,sensor,x,y,z,envelope,motion\n")
            for(s in engine.raw) append(String.format(Locale.US,"%.6f,%s,%.7f,%.7f,%.7f,%.9f,%s\n",s.seconds,engine.sensorName,s.x,s.y,s.z,s.envelope,s.motion))
        }
        val events=buildString {
            append("seconds,pattern,short_ms,long_ms,baseline_ms,segment_start_s,segment_end_s\n")
            for(e in snapshot.events) append(String.format(Locale.US,"%.6f,SHORT_LONG,%d,%d,%d,%.6f,%.6f\n",e.seconds,e.shortMs,e.longMs,e.baselineMs,(e.seconds-5).coerceAtLeast(0.0),(e.seconds+5).coerceAtMost(snapshot.seconds)))
        }
        val markers=buildString {
            append("seconds,kind\n")
            for(m in engine.markers) append(String.format(Locale.US,"%.6f,USER_SYMPTOM\n",m.seconds))
        }
        val report="""
            CardioPetto 0.2 — registrazione sperimentale
            Algoritmo: ${RhythmAnalyzer.VERSION}
            Data esportazione: ${Date()}
            Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            Sensore: ${engine.sensorName}
            Durata: ${String.format(Locale.US,"%.1f",snapshot.seconds)} s
            Campioni: ${snapshot.samples}
            Eventi temporali breve-lungo: ${snapshot.events.size}
            Marcatori sintomo manuali: ${snapshot.markers}
            Finestre analizzate (sovrapposte): ${engine.analyzedWindows}
            Finestre scartate: ${engine.rejectedWindows}
            Ultima finestra: ${snapshot.analysis.reason}

            Non è un ECG. Gli eventi sono coppie di intervalli breve-lungo tra picchi
            meccanici candidati, NON extrasistoli identificate. Possono essere artefatti.
            Nessuna probabilità clinica è stata stimata. Sensibilità e specificità sconosciute.
            Zero eventi non esclude extrasistoli né altre aritmie. Non valuta soffi.
            Se avverti episodi ricorrenti, discutine con il medico per valutare ECG/Holter.
            Il pulsante sintomo e il movimento del telefono possono disturbare il segnale.
            raw.csv conserva l'intera acquisizione; events.csv indica i segmenti da esaminare.
            Tutti i tempi sono secondi dall'inizio dell'acquisizione sensore.
        """.trimIndent()
        try {
            ZipOutputStream(temporary.outputStream()).use { zip ->
                for((name,text) in listOf("raw.csv" to raw,"events.csv" to events,"symptoms.csv" to markers,"report.txt" to report)) {
                    zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry()
                }
            }
            check(temporary.renameTo(file)) { "Impossibile completare il salvataggio" }
            return file
        } finally { temporary.delete() }
    }
}
