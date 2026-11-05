# Pavo AI — pacchetto per Claude

Fa usare **Pavo AI** a Claude, dal tuo computer. Il login lo fa il tuo browser:
si apre una finestra, entri, e la sessione resta salvata.

Due pacchetti, per due usi diversi:

| cartella | per cosa | come si installa |
|---|---|---|
| `pavo-claude/` | Claude Desktop e Claude Code | vedi sotto |
| `pavo-claude.mcpb` | Claude Desktop, un click | doppio clic sul file |

## Installazione

### Claude Desktop — con un click

Scarica `pavo-claude.mcpb` e aprilo con Claude Desktop. Fa tutto lui.

### Claude Desktop — a mano

Aggiungi al file `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "pavo": {
      "command": "python3",
      "args": ["/PERCORSO/COMPLETO/pavo-claude/pavo_mcp.py"]
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

Serve il **percorso assoluto**: `python3` non capisce `~`. Poi riavvia Claude.

### Claude Code

Apri la cartella `pavo-claude/` e basta: Claude Code vede i moduli. Oppure:

```bash
claude mcp add pavo -- python3 /PERCORSO/COMPLETO/pavo-claude/pavo_mcp.py
```

## Il login

```bash
pip install mcp      # serve solo per il server MCP
python3 login.py     # apre il browser
```

Si apre Chrome (o Chromium, Edge, Brave: il primo che trova) su Pavo. Fai il
login — Google o email — e la sessione si salva da sola.

**La password la digiti solo tu, dentro quella finestra.** Claude non la vede.

Per controllare, o rifare il login quando scade (~30 giorni):

```bash
python3 login.py --stato
python3 login.py
```

Da Claude, senza terminale: gli strumenti `token_stato` e `rinnova_token`.

## Cosa sa fare

**Canvas** — la generazione libera per nodi. Quattro tipi (testo, immagine,
video, audio) che si collegano tra loro: il testo entra nell'immagine,
l'immagine entra nel video. 29 modelli, 60 stili.

* i vincoli si leggono prima di generare: `canvas_valida` ti dice se 20 secondi
  su `seedance-2-0` sono ammessi (non lo sono: massimo 15)
* coerenza dei personaggi con gli asset e il foglio a quattro viste, richiamati
  con `@` nel prompt
* le 13 guide ufficiali, con i prompt che usa Pavo stesso

**Prompt** — `canvas_migliora_prompt` riscrive un prompt in inglese (gratis),
`canvas_guide` mostra come scrive Pavo.

**Flusso a copione** — progetto, copione, storyboard, episodi. Con la regola
della chat unica: una sola chat per serie, perche' la cronologia e' il contesto.

**Correzioni** — `correzioni.json` contiene le guardie: regole che intercettano
gli errori tipici prima che il prompt parta. Sono dati, non codice.

## Strumenti MCP

51 in tutto. I principali:

| strumento | cosa fa |
|---|---|
| `token_stato` | il token e' valido? quando scade? |
| `rinnova_token` | rifa' il login aprendo il browser |
| `saldo_crediti` | quanti crediti restano |
| `canvas_modelli` | i modelli del Canvas, con i vincoli |
| `canvas_valida` | i parametri stanno nei vincoli? |
| `canvas_guide` | le guide ufficiali di Pavo |
| `canvas_crea_asset` | crea personaggio, scena o oggetto |
| `canvas_quattro_viste` | foglio a 4 viste di un personaggio |
| `canvas_migliora_prompt` | riscrive un prompt in inglese |
| `chiamata_grezza` | qualsiasi endpoint, se manca uno strumento |

## Sicurezza

`pavo_login/pavo_state.json` contiene **token e cookie**: e' una credenziale,
vale come una password.

* non metterlo su Git (`.gitignore` lo esclude gia')
* non incollarlo in una chat
* non condividerlo

Anche il profilo del browser (`pavo_login/pavo_profile/`) contiene cookie di
sessione: stesso trattamento.

Per revocare tutto, fai logout da Pavo nel browser.

## Se qualcosa non funziona

| sintomo | rimedio |
|---|---|
| `non trovo un browser` | `python3 login.py --browser /percorso/del/browser` |
| `la porta 9222 e' occupata` | `python3 login.py --porta 9333` |
| `token scaduto` | `python3 login.py` |
| `No module named 'mcp'` | `pip install mcp` |
| Claude non vede gli strumenti | percorso non assoluto, o non riavviato |

## Avvertenza

Questi sono **endpoint interni** di Pavo, non una API pubblica documentata.
Funzionano oggi, ma se Pavo cambia qualcosa si rompono senza preavviso, e non
c'e' supporto.

## Licenza

MIT.
