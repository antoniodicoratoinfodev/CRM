# VoidReach — riepilogo dell'aggiornamento

Aggiornamento successivo: [revisione critica della preview Markdown e dei font](TYPOGRAPHY_PREVIEW_REVIEW.md), con nuovo renderer, famiglie incluse, gerarchia tipografica condivisa e verifiche dedicate.

Verifica locale: 6 settembre 2026. Implementato il [piano approvato](IMPLEMENTATION_PLAN.md), preservando le modifiche già presenti nel progetto. I test usano dati sintetici: nessun account reale è stato aperto o migrato.

## Cosa cambia

- **Interfaccia:** gerarchia visiva più chiara, spaziature e controlli condivisi nei quattro temi, sidebar adattive, ricerca globale e un nuovo marchio vettoriale usato anche nelle icone native.
- **Calendar:** viste Giorno/Settimana/Mese/Agenda, appuntamenti sovrapposti su colonne separate, intestazioni e fascia giornaliera fisse, eventi multi-giorno, selezione di intervalli, trascinamento/ridimensionamento annullabili e preferenze su orari lavorativi, inizio settimana e scatti temporali.
- **Pianificazione:** priorità, stato, scadenze facoltative, ricorrenze giornaliere/settimanali/mensili modificabili per singola occorrenza o porzione di serie, promemoria rinviabili, import/export ICS con segnalazione delle funzionalità non supportate.
- **Contatti e note:** profilo con cronologia datata delle interazioni e collegamenti al lavoro, creazione di follow-up, modelli per note e modalità Focus.
- **Home, Dashboard e Tasks:** riepiloghi azionabili, carico dei prossimi giorni, filtri e paginazione; le giornate molto dense aprono l'Agenda virtualizzata e la mini-agenda costruisce al massimo 50 schede.
- **Affidabilità:** salvataggio asincrono con feedback coerente, attesa dell'ultimo salvataggio prima di chiusura/export/logout, bozza conservata nei moduli non validi, undo/redo, cestino persistente e checkpoint prima di importazione/ripristino.
- **Protezione facoltativa:** cifratura locale autenticata, migrazione dei file gestiti e recupero con chiave offline. L'attivazione richiede una scelta esplicita in Impostazioni; non è stata attivata sui dati dell'utente.

## Verifiche completate

| Controllo | Esito |
|---|---|
| Build Maven e suite ordinaria | 98 test superati, nessun errore |
| Suite grafica opzionale | 4 scenari superati, quattro temi e quattro viste Calendar |
| Interazioni in finestra JavaFX reale | Bozze non valide, ricorrenze, undo/redo, cestino e collegamenti, Focus, salvataggio finale |
| Dati densi | 10.000 task/appuntamenti; algoritmo di sovrapposizione con 20.000 eventi |
| Compatibilità e archiviazione | Lettura schema precedente, scritture atomiche, errori di chiusura/export, import danneggiati, cifratura/recupero/manomissione |
| Icone native | Generazione e verifica degli asset superate separatamente |
| Pacchetto Windows | App-image generata con JDK 26; JAR distribuito identico alla build verificata |

Comandi, eseguiti dalla cartella `VoidReach-CRM-Final-No-FatJar`:

```text
mvn -B -q package
mvn -B -q -Dtest=WorkspaceUiIT test
mvn -B -q -Dtest=BrandAssetsIT test
```

Le due suite grafiche si eseguono separatamente e richiedono un desktop grafico. Gli screenshot aggiornati si trovano in [sample/screenshots](sample/screenshots); esempi: [mese](sample/screenshots/calendar-month.png), [sovrapposizioni](sample/screenshots/calendar-day.png), [Focus](sample/screenshots/notes-focus.png), [accesso](sample/screenshots/login.png).

## Avvio della build verificata

Chiudi l'eventuale versione precedente lasciandole completare il salvataggio, quindi avvia:

```text
VoidReach-CRM-Final-No-FatJar/target/native-icon-snap-20260906/VoidReach/VoidReach.exe
```

È un'applicazione portabile con runtime incluso, non un installer: conserva l'intera cartella `VoidReach`, non soltanto l'eseguibile. Come tutti gli output sotto `target`, viene rimossa da `mvn clean`; il README descrive gli script per rigenerare i pacchetti.

## Scelta icona e snap

- Menu account in alto a destra → **App icon**: **Book (original)** o **V (modern)**. Preferenza per account, applicata subito al marchio interno e alle icone delle finestre; non modifica il file `.exe` o i collegamenti fissati sulla barra delle applicazioni.
- Calendar, vista Giorno/Settimana → **Snap**: **Grid (15 min)**, **1**, **5**, **10**, **15** minuti; mantenuta anche l'opzione precedente da **30** minuti. La stessa scelta è disponibile nelle preferenze del Calendar.
- Lo snap allinea l'orario risultante anche quando la task era fuori griglia. Vale per trascinamento e ridimensionamento; Alt passa temporaneamente a 1 minuto. Test aggiunti per tutte le modalità, bordi della giornata, ridimensionamento, piccoli movimenti, menu icone e persistenza.

Schermate verificate: [libro originale](sample/screenshots/book-icon.png) e [selettore Snap nel Calendar](sample/screenshots/calendar-snap.png).

## Limiti da conoscere

- I promemoria funzionano solo mentre l'app è aperta; non sono notifiche del sistema operativo.
- ICS è un import/export locale, non una sincronizzazione con Google/Outlook. Regole avanzate e alcune combinazioni di fuso orario/ricorrenza vengono segnalate e saltate, non interpretate in modo approssimativo.
- La cifratura è facoltativa. Esportazioni portabili, dati identificativi del profilo e avatar restano in chiaro. Per un backup completo protetto servono anche i metadati dell'account con le chiavi avvolte: vedere [README](README.md#accounts-and-authentication).
- Il nuovo formato legge i dati precedenti; versioni più vecchie dell'app potrebbero non conservare i campi aggiuntivi.
- Il pacchetto è stato generato su Windows e l'interfaccia JavaFX verificata su Windows. Avvio dell'app-image in un altro ambiente, comportamento nativo macOS/Linux e audit indipendente di sicurezza rimangono da verificare.
