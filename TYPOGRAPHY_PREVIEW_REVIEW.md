# VoidReach — revisione critica di preview e tipografia

6 settembre 2026. Revisione del codice, implementazione e controllo di schermate JavaFX con dati sintetici. Nessuna modifica alle note dell'account reale.

## Valutazione

L'idea di separare scrittura e lettura era giusta. La vecchia preview, però, si comportava come una collezione di righe formattate: mancavano regole affidabili per paragrafi, titoli minori, elenchi annidati e tabelle. La sua estetica dipendeva inoltre da impostazioni dell'editor che avrebbero dovuto restare indipendenti.

Il problema dei font non era trovare un carattere più decorativo: mancavano gerarchie stabili. Dimensioni di 8–10 px, pesi fino a 900 e famiglie diverse per singoli pulsanti rendevano l'interfaccia disomogenea. Le dichiarazioni replicate fra temi rendevano anche difficile correggere un dettaglio senza creare differenze altrove.

## Correzioni alla preview

| Problema osservato | Intervento |
|---|---|
| Parsing riga per riga, limitato a H1–H3 | Parser CommonMark con H1–H6, paragrafi, liste ordinate/annidate, citazioni, tabelle e checklist |
| Testo non selezionabile | Superficie WebView con selezione e Ctrl+C; comando per copiare il testo e pulsanti per il codice |
| Grassetto, corsivo e interlinea ereditati dall'editor | Lettura indipendente: enfasi determinata dal Markdown, famiglia/dimensione/colore personalizzabili per nota |
| Cambio dimensione che fissava involontariamente il colore | Solo una scelta esplicita nel selettore colore crea un override |
| Cambio tema incompleto con un colore personalizzato | Aggiornamento della palette dell'intero documento, conservando il colore scelto |
| Link esterni che copiavano soltanto l'indirizzo | Apertura esplicita nel browser/client mail; messaggi per riferimenti a note mancanti o ambigui |
| Normalizzazione implicita dei backtick | Rendering standard senza riscrivere la sorgente; blocchi recintati per conservare righe e rientri |
| Comandi di scrittura presenti in lettura | Edit/Preview nella testata; barra Markdown nascosta in lettura |
| Metadata e aspetto troppo ravvicinati | Altezza della testata basata sul contenuto e righe di controlli adattive |

La lettura usa una colonna limitata in larghezza, interlinea 1,65, titoli con gerarchia misurata, codice a spaziatura fissa e scorrimento orizzontale per contenuti larghi. I cambi di tema/stile non ricaricano il documento e conservano la posizione. Il parsing avviene fuori dal thread grafico; risultati superati vengono ignorati. Un errore non lascia disponibile la copia del testo della nota precedente.

## Sistema tipografico

- **Inter:** interfaccia, testi normali e lettura.
- **JetBrains Mono:** sorgente Markdown e blocchi di codice.
- **Scala:** titoli pagina 28 px; titolo Calendar 24 px; pannelli 15 px; interfaccia 13 px; testi secondari 12 px; didascalie compatte 11 px. Il piccolo contatore delle notifiche rimane a 10 px.
- **Pesi:** Regular per il corpo, Medium per enfasi discreta, Semibold per titoli e azioni principali. Bold riservato a contenuti/controlli che lo richiedono.
- **Implementazione:** rimosse 473 dichiarazioni tipografiche sparse e duplicate. Un solo foglio condiviso stabilisce i font di tutti i temi, dei dialoghi, dell'accesso e dello splash.
- **Finiture:** titoli delle note meno pesanti, metadati più leggibili, numeri delle metriche meno invasivi, tempi degli eventi distinguibili, intestazioni delle tabelle coerenti, dimensioni dei font visualizzate senza decimali inutili.

I file dei font sono inclusi nell'app. Durante la verifica è emerso che JavaFX risolveva Medium e Semibold di Inter come Regular se richiesti sulla famiglia base: ora vengono selezionate le famiglie reali delle varianti. Le impostazioni personali già salvate non vengono sovrascritte. “Default” sceglie il font appropriato al contesto.

## Confini e compromessi espliciti

- WebView aggiunge peso al pacchetto rispetto ai vecchi TextFlow; viene creato soltanto alla prima apertura della preview.
- HTML grezzo mostrato come testo, JavaScript disabilitato, indirizzi non supportati bloccati. Le immagini remote vengono richieste soltanto con un clic esplicito.
- Le note sono record interni del workspace: percorsi relativi/locali delle immagini non hanno una cartella allegati da cui essere risolti. Compare un segnaposto; non viene inventato un percorso.
- Le checklist sono di sola lettura; per modificarle si torna all'editor.
- I collegamenti fra note sono basati sul titolo: non si tratta di compatibilità completa con Obsidian.
- La colorazione del codice è un lexer leggero e non un language server. I blocchi senza linguaggio e il codice inline restano neutri.
- Note oltre due milioni di caratteri restano disponibili nell'editor ma non vengono renderizzate.
- I font installati scelti manualmente possono differire fra computer. Le due famiglie predefinite sono invece incluse.
- Verifica effettuata su Windows; nessuna pretesa di aver collaudato il rendering nativo su macOS/Linux.

## Verifica

- Suite ordinaria: 105 test, inclusi 7 nuovi test su parsing, codice, wiki link e indirizzi non sicuri.
- Suite grafica: 6 scenari, comprendenti i flussi preesistenti di Calendar, ricerca, note, salvataggio, icone e snap.
- Schermate di tutte le sette sezioni nei quattro temi; preview chiara/scura/blu/grigio-blu, codice, aspetto espanso in finestra stretta, accesso e dialoghi.
- Prove sul DOM reale: selezione e copia con Ctrl+C, codice con rientri esatti, link, font effettivi, separazione editor/lettura, colore automatico, scorrimento conservato, errori e risultati asincroni superati.
- Pacchetto Windows creato in VoidReach-CRM-Final-No-FatJar/target/native-typography-preview-20260906/VoidReach. JAR distribuito identico alla build verificata (SHA-256: 031B063E86BA8E4997895C4709DE0483695DA047AEDF894047732A3E2B94EB15).
- Smoke test anche con il runtime del pacchetto e i soli JAR distribuiti: font caricati, WebView nativo, tabella Markdown e screenshot verificati. L'helper Java temporaneo di verifica non è incluso nella distribuzione finale.

Schermate: [gallery](sample/screenshots). Test riproducibili nella cartella del modulo: mvn -B -q test e mvn -B -q -Dtest=WorkspaceUiIT test.

## Riferimenti tecnici

Parser ed estensioni: [commonmark-java 0.30.0](https://github.com/commonmark/commonmark-java/releases/tag/commonmark-parent-0.30.0). Caricamento e risoluzione dei font: [JavaFX Font](https://openjfx.io/javadoc/26/javafx.graphics/javafx/scene/text/Font.html). Motore di lettura: [JavaFX WebEngine](https://openjfx.io/javadoc/26/javafx.web/javafx/scene/web/WebEngine.html). Provenienza e licenze: [font inclusi](VoidReach-CRM-Final-No-FatJar/src/main/resources/fonts/README.md).
