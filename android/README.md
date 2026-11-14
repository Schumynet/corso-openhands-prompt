# Pavo MCP — app Android

Un'app Android che fa due cose:

1. **Apre il browser dentro l'app** (WebView) e ti fa entrare in Pavo. La
   pagina si presenta **come sul computer**, non come sul telefono: user-agent
   da PC, viewport largo da portatile, e il pizzico per ingrandire. La password
   la digiti solo tu, nella pagina vera di Pavo: l'app non la vede e non la
   conserva. Quello che salva è il token di sessione che Pavo scrive dopo il
   login.
2. **Accende un server MCP** sul telefono, con 22 strumenti per usare la suite
   Pavo (Canvas, crediti, modelli, progetti, correzione di prompt). Il server
   ha un backend integrato: parla con l'API di Pavo lui stesso, quindi non
   serve nessun altro programma acceso.

## Perché un'app e non uno script

Il token di sessione di Pavo scade. Uno script sul computer richiede di
rifare il login ogni volta. Qui il login lo fai una volta, nel browser
dell'app, e il server resta acceso in background.

## Come si usa

1. Installa l'APK sul telefono (`adb install pavo-mcp-1.3.0.apk`, oppure apri
   il file sul telefono e consenti l'installazione da sorgenti sconosciute).
2. Apri l'app, tocca **Entra in Pavo**.
3. Fai il login nella pagina che compare. L'app riconosce il token e chiude da
   sola.

La pagina si apre in **vista da computer**. Serve, non è un'opzione: da
telefono Pavo mostra la versione mobile, che rimanda alla pagina di scaricamento
della sua app invece di far entrare. Con la vista da PC si entra.

L'interruttore **Vista da computer** in basso la spegne, ma lascialo acceso. Il
pizzico per ingrandire funziona in entrambi i casi.

L'accesso si fa con Google. Il pulsante apre una **finestra dentro l'app**, e il
consenso va completato lì. Serve: la libreria di Google apre il consenso in una
finestra nuova, e senza una finestra che la ospiti il tocco non fa nulla. Se
Google mostrasse un avviso di sicurezza, spegni e riaccendi l'interruttore
**Vista da computer** e riprova.

4. Tocca **Accendi il server**.
5. La schermata mostra l'indirizzo e il token di accoppiamento. Tocca **Copia
   configurazione** e incolla il risultato nel tuo client MCP.

Il pezzo da incollare è questo:

```json
{
  "mcpServers": {
    "pavo": {
      "url": "http://192.168.1.50:8787/mcp",
      "headers": {
        "Authorization": "Bearer <token-di-accoppiamento>"
      }
    }
  }
}
```

Sostituisci l'indirizzo con quello che mostra l'app: dipende dalla rete.

## Il token di accoppiamento

Il server ascolta sulla rete locale, e la rete locale è condivisa. Senza
controllo, chiunque sul tuo Wi-Fi potrebbe usare i tuoi crediti Pavo. Il token
di accoppiamento impedisce questo: senza, il server risponde 401.

Se preferisci non esporlo affatto, spegni **Raggiungibile dalla rete locale**:
il server ascolta solo su `127.0.0.1`, cioè solo dal telefono stesso.

## Strumenti

22 strumenti, fra cui:

| Strumento | Cosa fa |
|---|---|
| `saldo_crediti` | Quanti crediti hai |
| `elenca_modelli` | I 25 modelli disponibili |
| `costo_modello` | Quanto costa una generazione, **prima** di lanciarla |
| `canvas_modelli` | I modelli Canvas per tipo, con i vincoli |
| `canvas_valida` | Controlla durata, rapporto, risoluzione prima di spendere |
| `canvas_guide` | Le guide dei formati |
| `correggi_prompt` | Corregge un prompt seguendo le regole Pavo |
| `chiamata_grezza` | Qualunque endpoint non coperto dagli altri |

`canvas_valida` è il più utile: un modello che accetta al massimo 15 secondi
rifiuta 20 secondi, ma il rifiuto arriva *dopo* aver speso i crediti. Qui lo
sai prima.

## Compilare da sorgente

Serve JDK 17 e l'SDK Android 34. Poi:

```bash
./compila.sh
```

Lo script trova da sé l'SDK, compila, e mette l'APK in `dist/`.

## Com'è fatto

```
app/src/main/java/dev/schumynet/pavo/
├── MainActivity.java      schermata principale: stato, indirizzo, token
├── LoginActivity.java     il WebView per il login
├── ServerService.java     servizio in primo piano che tiene acceso il server
├── Sessione.java          token e impostazioni nelle preferenze
└── core/                  il cuore, senza dipendenze da Android
    ├── MiniJson.java      lettura e scrittura JSON
    ├── PavoClient.java    client dell'API Pavo
    ├── CanvasSpecs.java   vincoli dei modelli Canvas
    ├── ToolRegistry.java  i 22 strumenti
    └── McpServer.java     server HTTP MCP, su socket grezzo
```

`core/` non dipende da Android di proposito: così si compila e si prova su un
computer normale, molto più in fretta che su un telefono. Il server HTTP è
scritto su `ServerSocket` e non su `com.sun.net.httpserver`, che su Android non
esiste.

## Collaudo

```bash
./collauda.sh
```

Esegue 53 prove: il JSON, i vincoli Canvas, il server MCP su socket vero, e il
client contro l'API vera di Pavo. In più `collaudo_client_mcp.py` fa parlare al
server il **client ufficiale MCP dell'SDK**, che è il giudice vero: se il
client ufficiale non lo accetta, il server non serve a niente.

## Nota sull'APK

L'APK è firmato con la chiave di debug di Android. Va bene per installarlo a
mano; non va pubblicato su un negozio. Per quello serve una chiave tua.
