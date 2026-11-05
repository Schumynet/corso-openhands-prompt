---
name: pavo-suite
description: Use when you need to work with Pavo AI (app.pavo-ai.work) — log in from a fresh OpenHands profile, use Canvas mode (nodes, models, styles, character assets), generate or fix video prompts, apply prompt corrections, drive Pavo from Claude, or check credits, models and projects. Triggers on "Pavo", "pavo-ai", "login Pavo", "sessione Pavo", "token scaduto", "Canvas", "modalita Canvas", "correzioni prompt", "guardie prompt", "genera video Pavo", "MCP Pavo", "skill Pavo".
---

# Pavo Suite — login, Canvas, prompt, correzioni

Questa skill e' **autosufficiente**: incollata in un profilo OpenHands nuovo,
genera i file che le servono e apre il browser per il login. Non da' per scontato
niente e non contiene credenziali.

## Cosa fare, appena la incolli

Due comandi, in quest'ordine.

```bash
bash .agents/skills/pavo-suite/prepara.sh --avvia
```

`prepara.sh` installa quello che manca (Xvfb, x11vnc, chromium, websockify,
websocket-client), scarica noVNC da solo, ricava l'indirizzo pubblico del
runtime, e poi `--avvia` apre subito il browser. Alla fine stampa:

```
APRI QUESTO LINK NEL TUO BROWSER:

  https://work-1-<sandbox>.prod-runtime.all-hands.dev/vnc.html?autoconnect=1&resize=scale
```

Apri quel link e fai il login a Pavo. **La password la digiti solo tu, dentro
quella pagina**: non passa mai da questo codice. La sessione si salva da sola in
`pavo_login/pavo_state.json`.

Poi verifica:

```bash
python3 -c "import sys; sys.path.insert(0,'.'); import pavo_api as A; print(A.saldo(A.Pavo()))"
```

## Le quattro cose che questa skill fa

### 1. Aprire la sessione (browser + login)

| comando | cosa fa |
|---|---|
| `bash prepara.sh --avvia` | prepara tutto e apre il browser |
| `bash prepara.sh` | prepara e basta |
| `bash avvia_sessione.sh` | apre la sessione (prepara deve essere gia' passato) |
| `bash ferma_sessione.sh` | chiude tutto |
| `python3 salva_sessione.py --cdp 9222` | rilegge il token, se il browser e' gia' aperto |

Un altro account, con profilo e sessione separati:

```bash
ACCOUNT=secondo PORT=12001 DISPLAY_NUM=98 VNC_PORT=5901 CDP_PORT=9223 \
    bash avvia_sessione.sh
```

Diventano `pavo_login/pavo_profile_secondo/` e `pavo_state_secondo.json`.

#### REGOLA DI FERRO sulle schede

**Il browser serve a una cosa sola: stare su `app.pavo-ai.work` per il login.
Non apre chat, non apre schede, non naviga altrove.** Non e' un consiglio:
`guardia.py` gira ogni 3 secondi e la impone.

| situazione | cosa fa |
|---|---|
| scheda che non e' l'app | **la chiude** |
| si finisce su `/chat` | **riporta** alla radice |
| due schede dell'app | ne tiene **una** |

Gli host del login (`accounts.google.com`, `apis.google.com`) sono esclusi,
altrimenti il login con Google si romperebbe.

### 2. Correggere i prompt

Il problema: lo stesso errore torna in ogni clip. La soluzione: ogni errore
trovato in revisione diventa una **guardia**, iniettata nel prompt quando il
trigger compare.

Le guardie stanno in `correzioni.json` — sono **dati**, non codice. Il motore e'
`correzioni.py`, e non va toccato.

```bash
python3 correzioni.py --elenca                      # le guardie attive
python3 correzioni.py --valida                      # controlla il catalogo
python3 correzioni.py --prova "il pilota guida il kart verso la curva"
```

```python
from correzioni import applica, guardie_per_clip

# Il prompt con le guardie iniettate dopo l'azione.
prompt = applica("il pilota guida il kart verso la curva", clip=7)

# Solo la lista, per comporla diversamente.
lista = guardie_per_clip("il pilota guida il kart", clip=7)
```

**Aggiungere una guardia nuova.** Quando in revisione trovi un errore ricorrente,
non correggere la clip a mano: aggiungi la guardia a `correzioni.json`.

```json
{
  "nome": "mani_sul_volante",
  "testo": "Il pilota tiene entrambe le mani sul volante...",
  "trigger": ["guida", "volante"],
  "non_trigger": ["spettatore"]
}
```

Campi: `trigger` (basta una parola), `luoghi` o `luoghi_da_testo` (solo in certi
ambienti), `non_trigger` (spegne la guardia), `richiede_gesto` (scatta solo se
l'azione non ha gia' un gesto visibile), `solo_senza_personaggi` (solo clip di
ambiente).

Le guardie vanno **dopo** l'azione: il modello legge prima cosa mostrare, poi i
vincoli. Il contrario confonde le priorita'.

### 3. Parlare con Pavo

```python
import pavo_api as A

p = A.Pavo()
print(A.saldo(p))                                  # crediti
print(A.modelli(p))                                # 25 modelli
print(A.costo_stimato(p, "seedream4-5", "image"))  # costo vero
print(A.elenca_progetti(p))                        # i progetti
```

L'MCP per Claude, con 50 strumenti (37 del flusso a copione + 13 del Canvas):

```bash
python3 pavo_mcp.py     # server stdio
```

#### REGOLA DI FERRO sulle chat

**Una chat per volta, sempre la stessa.** Ogni chat e' una cronologia, e la
cronologia e' il contesto: una chat nuova per ogni clip significa che ogni clip
riparte da zero e non ricorda stile ne' personaggi.

```python
from chat_unica import chat_della_serie, con_chat

with con_chat("mio_progetto", titolo="Il mio progetto") as chat_id:
    ...   # genera, modifica, rileggi: sempre qui dentro
```

**Non chiamare mai `POST /v1/chat/conversation` direttamente.** Passa da
`chat_della_serie` o `con_chat`. Se chiedi di aprirne una quando ne esiste gia'
una, il modulo **rifiuta** e ti dice quale usare.

## Modalita' Canvas

Il Canvas e' il generatore libero: si lavora per **nodi** invece che dentro un
progetto a copione. Quattro tipi di nodo, e ognuno genera qualcosa:

| nodo | cosa produce | modelli |
|---|---|---|
| **Text** | testo, o da qui parte tutto | 9 modelli di testo |
| **Image** | immagini, anche da altre immagini | 10 |
| **Video** | video da testo, da immagine, da video | 8 |
| **Audio** | voce, musica, effetti | 2 |

I nodi si **collegano**: il testo entra nell'immagine, l'immagine entra nel
video. E' questo che il Canvas ha in piu' del flusso a copione: si costruisce
una catena e ogni passo vede l'uscita del precedente.

### Le tre modalita' video

| modo | cosa fa |
|---|---|
| `text_to_video` | solo dal testo |
| `reference_image` (Omni Reference) | mescola piu' riferimenti: personaggio + ambiente |
| `first_last_frame` | primo e ultimo fotogramma: il movimento e' vincolato |

Oltre a queste, alcuni modelli accettano `first_frame`, `reference_video`,
`video_edit` e `video_extend`.

### I vincoli si leggono, non si indovinano

Ogni modello ha durate, rapporti e risoluzioni suoi. Chiedere 20 secondi a
`seedance-2-0` e' un errore che costa un giro a vuoto: il modulo lo dice prima.

```python
import canvas as C

C.modelli("video")                          # tutti i modelli video
C.compatibili("video", "video_extend")      # chi sa allungare un video
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
`GET /pixa/canvas/tool-specs`, cosi' la skill non invecchia.

### Coerenza dei personaggi

E' il problema vero del video generato: lo stesso personaggio cambia faccia tra
una clip e l'altra. Il Canvas lo risolve con gli **asset**, che nel prompt si
richiamano con `@`.

```python
C.crea_asset(p, "characters", name="Mario", description="pilota, casco rosso")
C.quattro_viste(p, asset_id="...")     # foglio a 4 viste: frontale, profilo, retro, 3/4
```

Le quattro viste sono il modo per **fissare** un personaggio: da li' in poi lo
richiami e resta lo stesso. Lo stesso vale per `scenes` (ambienti) e `props`
(oggetti). Quattro abilita' immagine servono a questo: `character_setting`,
`character_four_view`, `scene_setting`, `product_setting`.

### Prompt

```python
C.migliora(p, "un kart che derapa in curva")   # -> inglese, dettagliato
C.traduci(p, "un kart che derapa in curva")    # -> cinese
C.espandi(p, url_immagine, tipo_asset="character")  # da immagine a descrizione
```

**Trappola:** `traduci` **ignora `target_language`**. La direzione e' sempre
`en2zh`, anche passando `'en'`: la risposta lo dichiara in `direction`. Non
fidarti del nome del parametro.

**Trappola:** `migliora` e `traduci` vogliono `items`, cioe' una **lista di
mappe** con discriminatore `type` e il testo in `content`:

```python
{"items": [{"type": "text", "content": "..."}]}
```

Con `{"prompt": "..."}` rispondono `400 Items is required`. Sembra un campo
semplice e non lo e'.

**Trappola:** `espandi` vuole **un'immagine**, non un testo. Chiede
`asset_type` e `image_url`; senza, `400 AssetType is required`.

### Le guide ufficiali

`GET /pixa/canvas/tool-specs` contiene 13 guide, e ognuna porta il **prompt che
usa Pavo stesso**. Sono la cosa piu' utile del file: mostrano come formula le
richieste chi ha costruito l'app. Copiarne la forma funziona meglio che
inventarne una.

```bash
python3 canvas.py --guide
```

Per esempio, per il primo/ultimo fotogramma Pavo usa:

> Image 1 is the first frame, and Image 2 is the last frame. Create a seamless
> transition from Image 1 to Image 2.

E per l'Omni Reference descrive il movimento **per intero**, passo per passo,
invece di nominarlo. E' un'indicazione su come scrivere: dire cosa succede,
non cosa si vuole ottenere.

## Fatti utili su Pavo

| cosa | valore |
|---|---|
| crediti | si leggono con `saldo()` |
| modelli | 25 in tutto: 15 immagine, 10 video |
| stili | 60, in 3 categorie (2D, 3D, Real Person) |
| voci | 20, costo 2 crediti |
| token | dura ~30 giorni, poi serve un nuovo login |

**Trappola:** `POST /aigc/credits/calculate` risponde **sempre 0**. Non e'
affidabile. Usa `POST /aigc/credits/estimate`, che da' il costo vero.

**Trappola:** `agnes-image` risulta a costo 0 ma risponde 500. Inutilizzabile.

L'elenco completo degli endpoint e' in `REFERENCE_ENDPOINT.md`.

## Portare tutto su un altro account OpenHands

L'indirizzo pubblico contiene il **nome del sandbox**, che cambia con l'account.
Per questo **non e' scritto da nessuna parte**: lo ricava `runtime.py`.

Ordine dei ripieghi: `PAVO_RUNTIME_HOST` -> `SANDBOX_ID` -> `HOSTNAME` (forma
`runtime-<sandbox>-<hash>-<hash>`) -> file `.runtime_host` -> valore noto.

Quindi su un altro account non serve cambiare niente. Controllo:

```bash
python3 runtime.py            # host, sandbox, i due indirizzi
python3 runtime.py --scrivi   # salva in .runtime_host
```

Se la ricostruzione non funziona, si forza:

```bash
PAVO_RUNTIME_HOST=nomesandbox.prod-runtime.all-hands.dev bash prepara.sh --avvia
```

**Attenzione:** la sessione Pavo e' legata all'account **Pavo**, non a quello
OpenHands. Su un account OpenHands nuovo serve un **nuovo login** su Pavo.

## Se qualcosa non funziona

| sintomo | causa | rimedio |
|---|---|---|
| "manca noVNC" | prepara non e' passato | `bash prepara.sh` |
| "il gateway non risponde" | websockify non e' partito | `cat logs/websockify.log` |
| "non trovo runtime.py" | skill spostata fuori dal progetto | rimetterla dentro |
| lo schermo e' nero | chromium non e' partito | `cat pavo_login/logs/chromium.log` |
| "nessuna scheda Pavo aperta" | non hai navigato sull'app | apri il link |
| la sessione non si salva | login non completo | controlla di essere loggato |
| token scaduto | sono passati ~30 giorni | `bash avvia_sessione.sh` |

I log stanno in `.agents/skills/pavo-suite/logs/` e `pavo_login/logs/`.

## Come funziona sotto

```
Xvfb :99              display virtuale, 1600x1000
   |
x11vnc :5900          serve quel display via VNC
   |
websockify :12000     porta il VNC su web (noVNC) -> il tuo browser
   |
chromium              apre app.pavo-ai.work nel display virtuale
   |
guardia.py            una sola pagina, nessuna chat
   |
salva_sessione.py     legge token e cookie via CDP, scrive pavo_state.json
```

Due dettagli che servono:

- **Playwright non serve.** Si usa il chromium di sistema col protocollo CDP:
  piu' leggero, nessun download. `/usr/bin/chromium`.
- **La connessione CDP vuole `suppress_origin=True`.** Chrome risponde 403 alle
  connessioni che dichiarano un `Origin`.

## Avvertenza

`pavo_login/pavo_state.json` contiene token e cookie: chi lo ha puo' usare
l'account. Non va in un repository pubblico e non si condivide. Il pacchetto
distribuito **non lo contiene**: ognuno fa il proprio login.
