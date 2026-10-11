# CardioPetto — prototipo Android sperimentale

Applicazione Android **Kotlin** per acquisire il giroscopio (o, in alternativa, l'accelerometro) con lo smartphone sul torace. Mostra il segnale meccanico, una stima **non validata** dei BPM e sequenze temporali breve-lungo tra picchi candidati. **Non identifica extrasistoli e non assegna probabilità diagnostiche.**

## Aprire e avviare

1. Apri questa cartella come progetto in **Android Studio**.
2. Installa Android SDK 35 e JDK 17 se richiesti dall'IDE.
3. Sincronizza Gradle. Il progetto è configurato per Android Gradle Plugin 8.9.2, Kotlin 2.1.20 e Gradle 8.11.1.
4. Collega **un telefono fisico Android 8+**, con debugging USB attivo; non usare l'emulatore per la misurazione.
5. Premi **Run**. Il progetto include i file Gradle ma non il binario `gradle-wrapper.jar`: Android Studio può sincronizzarlo con la propria distribuzione Gradle. Se si desidera la riga di comando, rigenerare il wrapper con `gradle wrapper --gradle-version 8.11.1` da un ambiente Gradle installato.

## Uso personale e analisi sperimentale 0.2

1. Sdraiati supino e appoggia il telefono sul torace, sopra un indumento sottile.
2. Avvia e resta fermo respirando normalmente. La sessione termina dopo 2 minuti; la prima analisi richiede almeno 12 secondi.
3. Il pulsante **HO AVVERTITO UN EPISODIO** aggiunge un marcatore temporale manuale. Il tocco può introdurre artefatti.
4. Il display distingue una finestra analizzabile da segnale debole, movimento, campionamento interrotto o pulsazioni ambigue.
5. Gli eventi breve-lungo sono **irregolarità del segnale**, non extrasistoli diagnosticate. Zero eventi non esclude aritmie; anche una finestra analizzabile può contenere artefatti.
6. Termina e salva. **Condividi ultima sessione** o **Sessioni salvate** esportano uno ZIP completo: `raw.csv`, `events.csv`, `symptoms.csv`, `report.txt`. I segmenti di 5 secondi prima/dopo ogni evento sono indicizzati nel CSV; l'intero segnale resta in `raw.csv`.

I file rimangono nella memoria privata dell'app e vengono condivisi soltanto su richiesta, tramite URI con accesso temporaneo in lettura. Nessun accesso Internet o microfono. Non caricare sessioni personali nel repository pubblico.

## Cosa fa e cosa non fa l'algoritmo

- Usa giroscopio, oppure accelerometro; rimuove la componente lenta e calcola un inviluppo.
- Interpola a 50 Hz soltanto campioni contigui, controllando anche i timestamp originali; rifiuta finestre con interruzioni o frequenza di acquisizione insufficiente.
- Analizza gli ultimi 18 secondi, cercando picchi separati da almeno 280 ms e una morfologia localmente coerente.
- Cerca una coppia di intervalli <78% e >120% della mediana degli intervalli precedenti, con somma vicina a due intervalli di riferimento. È una **regola euristica**, non un modello probabilistico calibrato.
- Conserva gli eventi una sola volta per sessione. Il conteggio è cumulativo; la qualità mostrata riguarda l'ultima finestra.
- Non riconosce l'origine atriale o ventricolare, non rileva tutte le extrasistoli (incluse quelle senza la pausa cercata), non distingue in modo validato il segnale cardiaco dagli artefatti e non valuta soffi.

Per trasformarlo in un rilevatore clinicamente attendibile servono acquisizioni simultanee con ECG annotate, valutazione dei falsi positivi/negativi e validazione su registrazioni indipendenti e dispositivi diversi. I test sintetici verificano il software, **non la validità clinica**. Non modificare farmaci o attività in base ai risultati.

Per episodi ricorrenti consulta il medico per valutare ECG/Holter. In presenza di dolore toracico, svenimento o forte difficoltà respiratoria cerca assistenza urgente (112), senza attendere la misura.

## Verifica tecnica

La CI esegue `:app:testDebugUnitTest` e `:app:assembleDebug`. I test coprono ritmo sintetico regolare, coppia breve-lungo, picco mancante, movimento, interruzioni, timestamp non validi, segnale piatto, campionamento lento e artefatto impulsivo. Le soglie non sono validate clinicamente. Il comportamento su telefono fisico resta da verificare.

## APK debug da GitHub Actions

Il workflow **Android Debug APK** compila automaticamente ogni push su `main` e ogni pull request verso `main`. Può anche essere avviato da **Actions → Android Debug APK → Run workflow**.

1. Apri **Actions** nel repository.
2. Apri una esecuzione completata con spunta verde.
3. In **Artifacts**, scarica **CardioPetto-debug** (richiede accesso a GitHub).
4. Estrai lo ZIP e apri **CardioPetto-debug.apk** sul telefono Android.

L'archivio include il checksum SHA-256; resta disponibile per 30 giorni.
La CI installa JDK 17 e Gradle 8.11.1 e compila con SDK 35 anche senza wrapper completo nel repository.
L'APK usa la firma debug generata dal runner: tra build diverse potrebbe essere necessario disinstallare la versione precedente (perdendo i dati locali), perché la chiave può cambiare. Non è una release per Google Play.
Una build riuscita verifica la compilazione, non l'accuratezza della stima cardiaca.
