---
name: pavo-claude
description: Use when you need to work with Pavo AI (app.pavo-ai.work) from Claude on your own computer — log in by opening your browser, use Canvas mode (nodes, models, styles, character assets), generate or fix video prompts, apply prompt corrections, or check credits, models and projects. Triggers on "Pavo", "pavo-ai", "login Pavo", "sessione Pavo", "token scaduto", "Canvas", "correzioni prompt", "genera video Pavo".
---

# Pavo per Claude — login, Canvas, prompt, correzioni

Questa cartella serve a far usare **Pavo AI a Claude**, sul tuo computer.

Il pezzo che cambia rispetto alla versione per OpenHands: **il login lo fa il tuo
browser**. Niente display virtuale, niente noVNC. Si apre una finestra, fai il
login a mano, e la sessione resta salvata.

## Cosa fare, appena la installi

Due comandi, in quest'ordine.

```bash
pip install mcp           # serve solo per il server MCP
python3 login.py          # apre il browser per il login a Pavo
```

Il secondo apre Chrome (o Chromium, Edge, Brave: il primo che trova) su Pavo.
Fai il login — Google o email — e la sessione si salva da sola in
`pavo_login/pavo_state.json`.

**La password la digiti solo tu, dentro quella finestra.** Claude non la vede e
non passa da nessuna parte.

Poi, per usare Pavo dagli strumenti:

```bash
python3 pavo_mcp.py       # server MCP, trasporto stdio
```

## Configurare Claude

### Claude Desktop

Aggiungi al file `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "pavo": {
      "command": "python3",
      "args": ["/PERCORSO/COMPLETO/pavo_mcp.py"]
    }
  }
}
```

Dove sta quel file:

| sistema | percorso |
|---|---|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

Metti il **percorso assoluto**: `python3` non capisce `~`. Poi riavvia Claude.

### Claude Code

Apri questa cartella e basta: Claude Code vede i moduli e li usa. Oppure:

```bash
claude mcp add pavo -- python3 /PERCORSO/COMPLETO/pavo_mcp.py
```

### Controllare che funzioni

Chiedi a Claude: *"che saldo ho su Pavo?"*. Se risponde con un numero, e' a
posto. Se dice che il token e' scaduto, rifai il login.

## Il login, quando scade

Il token dura **circa 30 giorni**, poi serve rifare il login.

```bash
python3 login.py --stato     # dice se e' ancora valido e quando scade
python3 login.py             # rifa' il login
```

Da Claude, senza terminale, ci sono due strumenti:

* `token_stato` — dice se il token e' valido, quando scade, e chi e' l'utente.
  Chiamalo **prima** di qualsiasi altra cosa.
* `rinnova_token` — apre il browser e rifa' il login. Serve che tu faccia il
  login a mano nella finestra che si apre.

### Se hai gia' un browser con il debug aperto

```bash
python3 login.py --niente-browser --porta 9222
```

Utile se Chrome e' gia' avviato con `--remote-debugging-port=9222` e non vuoi
aprirne un secondo. Il profilo usato e' separato da quello di tutti i giorni
(`pavo_login/pavo_profile/`), cosi' non si tocca la tua navigazione.

## Le quattro cose che sa fare

### 1. Aprire la sessione

| comando | cosa fa |
|---|---|
| `python3 login.py` | apre il browser e aspetta il login |
| `python3 login.py --stato` | dice se la sessione e' valida |
| `python3 login.py --porta 9333` | usa un'altra porta di debug |
| `python3 login.py --browser /percorso/chrome` | forza un browser preciso |

Se il browser non viene trovato da solo, passa `--browser`. Su macOS per esempio:

```bash
python3 login.py --browser "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
```

### 2. Canvas — la generazione libera per nodi

Il Canvas e' diverso dal flusso a copione: non c'e' un progetto con episodi, c'e'
una **tela di nodi** che colleghi tra loro. Quattro tipi di nodo:

| nodo | produce | modelli |
|---|---|---|
| **Text** | testo, o da qui parte tutto | 9 |
| **Image** | immagini, anche da altre immagini | 10 |
| **Video** | video da testo, immagine o video | 8 |
| **Audio** | voce, musica, effetti | 2 |

I nodi si **collegano**: il testo entra nell'immagine, l'immagine entra nel
video. Ogni passo vede l'uscita del precedente. Nel flusso a copione la catena
la decide Pavo; qui la decidi tu.

Tre modalita' video: `text_to_video` (solo testo), `reference_image` — *Omni
Reference* — che mescola piu' riferimenti (personaggio + ambiente), e
`first_last_frame`, dove dai primo e ultimo fotogramma e il movimento e'
vincolato. Alcuni modelli accettano anche `video_edit` e `video_extend`.

#### I vincoli si leggono, non si indovinano

Ogni modello ha durate, rapporti e risoluzioni suoi. Chiedere 20 secondi a
`seedance-2-0` e' un errore che costa un giro a vuoto: il modulo lo dice prima.

```python
import canvas as C

C.modelli("video")                            # tutti i modelli video
C.compatibili("video", "video_extend")        # chi sa allungare un video
C.valida("video", "seedance-2-0", durata=20)  # -> ['durata 20s non ammessa (scegli tra 4-15s)']
C.valida("video", "seedance-2-0", durata=10)  # -> []  tutto bene
```

Da riga di comando:

```bash
python3 canvas.py --modelli video
python3 canvas.py --stili
python3 canvas.py --guide
python3 canvas.py --compatibili video video_edit
python3 canvas.py --aggiorna     # riscarica le specifiche dall'app
```

Le specifiche stanno in `canvas_specs.json` (20 KB): 8 modelli video, 10
immagine, 2 audio, 9 di testo, 60 stili, 13 guide. `--aggiorna` le rifa' da
`GET /pixa/canvas/tool-specs`, cosi' non invecchiano quando Pavo aggiunge modelli.

#### Coerenza dei personaggi

E' il problema vero del video generato: lo stesso personaggio cambia faccia tra
una clip e l'altra. Il Canvas lo risolve con gli **asset**, che nel prompt
richiami con `@`.

```python
C.crea_asset(p, "characters", name="Mario", description="pilota, casco rosso")
C.quattro_viste(p, asset_id="...")   # frontale, profilo, retro, tre quarti
```

Le quattro viste sono il modo per **fissare** un personaggio: da li' in poi lo
richiami e resta lo stesso. Lo stesso vale per `scenes` (ambienti) e `props`
(oggetti).

#### Prompt

```python
C.migliora(p, "un kart che derapa in curva")        # -> inglese, dettagliato
C.traduci(p, "un kart che derapa in curva")         # -> cinese
C.espandi(p, url_immagine, tipo_asset="character")  # da immagine a descrizione
```

**Trappola:** `traduci` **ignora `target_language`**. La direzione e' sempre
`en2zh`, anche passando `'en'`. La risposta lo dichiara in `direction`.

**Trappola:** `migliora` e `traduci` vogliono `items`, cioe' una **lista di
mappe** con discriminatore `type` e il testo in `content`:

```python
{"items": [{"type": "text", "content": "..."}]}
```

Con `{"prompt": "..."}` rispondono `400 Items is required`.

**Trappola:** `espandi` vuole **un'immagine**, non un testo.

#### Le guide ufficiali

`canvas_specs.json` contiene 13 guide, e ognuna porta il **prompt che usa Pavo
stesso**. Sono la cosa piu' utile del file: mostrano come formula le richieste
chi ha costruito l'app.

```bash
python3 canvas.py --guide
```

### 3. Il flusso a copione

Oltre al Canvas c'e' il flusso classico: progetto, copione, storyboard, episodi.

```python
import pavo_api as A

p = A.Pavo()
print(A.saldo(p))                                  # crediti
print(A.modelli(p))                                # 25 modelli
print(A.costo_stimato(p, "seedream4-5", "image"))  # costo vero
print(A.elenca_progetti(p))                        # i progetti
```

**REGOLA DI FERRO sulle chat:** una chat per volta, sempre la stessa. Ogni chat
e' una cronologia, e la cronologia e' il contesto. Una chat nuova per ogni clip
significa che ogni clip riparte da zero e non ricorda stile ne' personaggi.

```python
from chat_unica import chat_della_serie, con_chat

with con_chat("mio_progetto", titolo="Il mio progetto") as chat_id:
    ...   # genera, modifica, rileggi: sempre qui dentro
```

### 4. Correggere i prompt

`correzioni.json` contiene le guardie: regole che intercettano gli errori tipici
**prima** che il prompt parta.

```bash
python3 correzioni.py --elenca                      # le guardie attive
python3 correzioni.py --valida                      # controlla il catalogo
python3 correzioni.py --prova "il pilota guida il kart verso la curva"
```

Le guardie sono **dati**, non codice: si aggiungono senza toccare il motore.

## Gli strumenti MCP

51 in tutto. I piu' importanti:

| strumento | cosa fa |
|---|---|
| `token_stato` | il token e' valido? quando scade? |
| `rinnova_token` | rifa' il login aprendo il browser |
| `percorso_sessione` | dove sta il file di sessione |
| `saldo_crediti` | quanti crediti restano |
| `canvas_modelli` | i modelli del Canvas, con i vincoli |
| `canvas_valida` | i parametri stanno nei vincoli? |
| `canvas_guide` | le guide ufficiali di Pavo |
| `canvas_crea_asset` | crea personaggio, scena o oggetto |
| `canvas_quattro_viste` | foglio a 4 viste di un personaggio |
| `canvas_migliora_prompt` | riscrive un prompt (gratis) |
| `elenca_modelli` | i modelli del flusso a copione |
| `chiamata_grezza` | qualsiasi endpoint, se manca uno strumento |

## Fatti utili su Pavo

| cosa | valore |
|---|---|
| crediti | si leggono con `saldo()` |
| modelli | 25 nel flusso a copione: 15 immagine, 10 video |
| Canvas | 29 modelli: 10 immagine, 8 video, 2 audio, 9 testo |
| stili | 60, in 3 categorie |
| voci | 20, costo 2 crediti |
| token | dura ~30 giorni, poi serve un nuovo login |

**Trappola:** `POST /aigc/credits/calculate` risponde **sempre 0**. Non e'
affidabile. Usa `POST /aigc/credits/estimate`, che da' il costo vero.

**Trappola:** `agnes-image` risulta a costo 0 ma risponde 500. Inutilizzabile.

L'elenco completo degli endpoint e' in `REFERENCE_ENDPOINT.md`.

## Sicurezza

`pavo_login/pavo_state.json` contiene **token e cookie**: e' una credenziale a
tutti gli effetti. Vale come una password.

* non metterlo su Git (`.gitignore` lo esclude gia');
* non incollarlo in una chat;
* non condividerlo.

Il profilo del browser (`pavo_login/pavo_profile/`) contiene anche i cookie di
sessione: stesso trattamento.

Per revocare tutto, fai logout da Pavo nel browser: il token muore subito.

## Se qualcosa non funziona

| sintomo | causa | rimedio |
|---|---|---|
| `non trovo un browser` | percorso non standard | `python3 login.py --browser /percorso` |
| `la porta 9222 e' occupata` | altro Chrome col debug | `--porta 9333` |
| `token scaduto` | passati ~30 giorni | `python3 login.py` |
| `No module named 'mcp'` | manca la libreria | `pip install mcp` |
| Claude non vede gli strumenti | percorso non assoluto, o non riavviato | correggi il JSON e riavvia |
| login non rilevato | scheda su altro sito | resta su `app.pavo-ai.work` |

Per vedere cosa succede davvero:

```bash
python3 login.py --stato
python3 -c "import sys; sys.path.insert(0,'.'); import pavo_api as A; print(A.scadenza_token(A.leggi_token()))"
```

## Come funziona sotto

```
login.py            apre il browser e salva la sessione (CDP, senza Playwright)
pavo_mcp.py         server MCP: 51 strumenti
pavo_api.py         client degli endpoint
canvas.py           modelli, vincoli e guide del Canvas
canvas_specs.json   le specifiche Canvas, copiate dall'app
chat_unica.py       la regola della chat unica
correzioni.py       il motore delle guardie
correzioni.json     le guardie (dati)
```

`login.py` non usa Playwright di proposito: serve solo aprire il browser e
leggere due cose dal suo protocollo di debug. Sono ~60 righe di WebSocket
scritte a mano, e cosi' non c'e' niente da installare oltre a `mcp`.

## Avvertenza

Questi sono **endpoint interni**, non una API pubblica documentata. Funzionano
oggi, ma se Pavo cambia qualcosa si rompono senza preavviso, e non c'e'
supporto. Usali sul tuo account e con giudizio.
