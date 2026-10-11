# CardioPetto — prototipo Android sperimentale

Applicazione Android **Kotlin** per acquisire il giroscopio (o, in alternativa, l'accelerometro) con lo smartphone sul torace. Mostra il segnale meccanico e una stima **non validata** dei BPM da autocorrelazione dell'inviluppo.

## Aprire e avviare

1. Apri questa cartella come progetto in **Android Studio**.
2. Installa Android SDK 35 e JDK 17 se richiesti dall'IDE.
3. Sincronizza Gradle. Il progetto è configurato per Android Gradle Plugin 8.9.2, Kotlin 2.1.20 e Gradle 8.11.1.
4. Collega **un telefono fisico Android 8+**, con debugging USB attivo; non usare l'emulatore per la misurazione.
5. Premi **Run**. Il progetto include i file Gradle ma non il binario `gradle-wrapper.jar`: Android Studio può sincronizzarlo con la propria distribuzione Gradle. Se si desidera la riga di comando, rigenerare il wrapper con `gradle wrapper --gradle-version 8.11.1` da un ambiente Gradle installato.

## Uso

1. Sdraiati supino, posiziona delicatamente il telefono sul torace, sopra una maglia sottile, evitando che cada.
2. Avvia la misurazione e resta immobile almeno 20–30 secondi.
3. Se il segnale non è sufficientemente periodico, vedrai «— BPM». Non forzare interpretazioni mediche.
4. Arresta per salvare un CSV nella cartella privata dell'app (external files). Il pulsante Condividi CSV copia testo nel foglio di condivisione Android (può essere troncato per sessioni molto grandi); il CSV locale resta completo fino al limite del buffer.

## Cosa fa l'algoritmo

- Richiede un periodo di campionamento di 10 ms (la frequenza effettiva dipende dal dispositivo).
- Rimuove la componente lenta da tre assi con un filtro passa-alto semplice.
- Calcola un inviluppo di energia levigato.
- Campiona l'inviluppo su una griglia di 50 Hz e cerca la periodicità con autocorrelazione in 40–130 BPM.
- Pubblica BPM solo con coefficiente di autocorrelazione almeno 0,55 e una finestra sufficiente.

**LIMITI IMPORTANTI:** non identifica con certezza i singoli battiti, può confondere respirazione, armoniche e movimento con la frequenza cardiaca, e la «qualità» è solo un punteggio algoritmico, non un valore clinico. Non è ECG e non deve essere usato per diagnosi, monitoraggio medico, emergenze o decisioni sanitarie. Va confrontato con un ECG/fascia toracica di riferimento su più persone e smartphone prima di affermarne l'accuratezza. La raccolta dei dati avviene sul dispositivo; non serve accesso Internet né microfono.

## Stato

**Codice sorgente prototipale, non compilato né testato su dispositivo in questo ambiente.** Miglioramenti successivi: timestamp binning robusto, fusione sensori, filtro adattivo, estrazione eventi cardiaci, scoring dell'artefatto, test unitari e validazione contro ECG.

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
