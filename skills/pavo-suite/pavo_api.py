#!/usr/bin/env python3
"""pavo_api — client completo per le API interne di Pavo AI.

Cosa fa
-------
Copre gli endpoint di `app.pavo-ai.work` come li usa l'app dal browser, cosi'
un agente puo' usare Pavo come farebbe una persona: guardare i crediti,
scegliere un modello, creare un progetto, generare immagini e video, montare
l'export.

Come funziona l'autenticazione
------------------------------
Pavo **non ha API key**. L'app autentica con il token di sessione del browser,
letto dal `localStorage` di `app.pavo-ai.work`. Il token e' un JWT che scade.
Quello attuale scade il 21 novembre 2026.

Ogni chiamata porta, oltre ad `Authorization: Bearer <token>`, cinque header
che il server pretende:

    x-platform: 1
    x-app-timezone: UTC
    x-user-language: en
    x-client-time-ms: <millisecondi attuali>
    Referer: https://app.pavo-ai.work/

Senza di essi il server rifiuta. Il token si legge dal file di sessione del
browser (`pavo_login/pavo_state.json`), quindi non va mai scritto nel codice.

I campi dei payload sono in snake_case (`model_code`, `style_id`,
`custom_style_prompt`, `upload_reference_image_url`). In PascalCase il server
rifiuta.

Nota
----
Questi sono endpoint interni, non una API pubblica documentata. Funzionano, ma
se Pavo cambia qualcosa si rompono senza preavviso.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

BASE_API = "https://api.pixa-ai.work/api/v1"
APP_ORIGIN = "https://app.pavo-ai.work/"


def _trova_stato() -> Path:
    """Il file di sessione, risalendo fino alla cartella che contiene `pavo_login`.

    Non si contano i livelli: questo modulo puo' stare in radice oppure dentro
    `.agents/skills/<nome>/`, e i livelli cambierebbero. Si cerca la cartella
    `pavo_login`, che e' unica e non ambigua — a differenza di `runtime.py`, di
    cui esiste una copia anche dentro la skill.
    """
    if os.environ.get("PAVO_STATO"):
        return Path(os.environ["PAVO_STATO"]).expanduser()
    d = Path(__file__).resolve().parent
    for _ in range(8):
        candidato = d / "pavo_login"
        if candidato.is_dir():
            return candidato / "pavo_state.json"
        if d.parent == d:
            break
        d = d.parent
    return Path(__file__).resolve().parent.parent / "pavo_login" / "pavo_state.json"


STATO_SESSIONE = _trova_stato()

# Codici di esito dell'API. `000000` e' successo; `000002` e' un errore di
# business (per esempio crediti insufficienti), non un errore HTTP.
ESITO_OK = "000000"


class PavoError(RuntimeError):
    """Errore dell'API Pavo, con il codice di business se c'e'."""

    def __init__(self, messaggio: str, http: int = 0, codice: str = ""):
        super().__init__(messaggio)
        self.http = http
        self.codice = codice


# --- Token -------------------------------------------------------------------

def leggi_token(percorso: Path | None = None) -> str:
    """Legge il token di sessione dal file del browser.

    Il token sta nel `localStorage` dell'origine dell'app. Se il file manca o
    non contiene il token, il messaggio dice cosa fare.
    """
    percorso = percorso or STATO_SESSIONE
    if not percorso.is_file():
        raise PavoError(
            f"file di sessione assente: {percorso}. "
            "Serve fare il login dal browser (pavo_login/save_session.py).")
    dati = json.loads(percorso.read_text(encoding="utf-8"))
    for origine in dati.get("origins", []):
        for voce in origine.get("localStorage", []):
            if voce.get("name") == "token" and voce.get("value"):
                return voce["value"]
    raise PavoError(
        "token non trovato nel file di sessione. Serve rifare il login.")


def scadenza_token(token: str) -> dict:
    """Scadenza del token, senza verificarne la firma.

    Il token e' un JWT: il payload si legge in base64. Non serve la chiave,
    perche' qui interessa solo sapere quando scade.
    """
    import base64

    try:
        parti = token.split(".")
        if len(parti) < 2:
            return {"valido": True, "scadenza": None}
        corpo = parti[1] + "=" * (-len(parti[1]) % 4)
        payload = json.loads(base64.urlsafe_b64decode(corpo))
    except Exception:
        return {"valido": True, "scadenza": None}
    scad = payload.get("exp")
    if not scad:
        return {"valido": True, "scadenza": None, "utente": payload.get("sub")}
    adesso = time.time()
    return {
        "valido": scad > adesso,
        "scadenza": time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime(scad)),
        "giorni_rimasti": round((scad - adesso) / 86400, 1),
        "utente": payload.get("sub"),
    }


# --- Client ------------------------------------------------------------------

class Pavo:
    """Client delle API Pavo. Una istanza per sessione."""

    def __init__(self, token: str | None = None, timeout: int = 60):
        self.token = token or leggi_token()
        self.timeout = timeout
        self.ultimo_http = 0

    def intestazioni(self, json_body: bool = False) -> dict:
        """Header che il server pretende, oltre ad Authorization."""
        h = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/json",
            "x-platform": "1",
            "x-app-timezone": "UTC",
            "x-user-language": "en",
            "x-client-time-ms": str(int(time.time() * 1000)),
            "Referer": APP_ORIGIN,
        }
        if json_body:
            h["Content-Type"] = "application/json"
        return h

    def chiama(self, metodo: str, percorso: str, corpo: dict | None = None,
               parametri: dict | None = None, ritenta: int = 3) -> Any:
        """Chiamata grezza. Restituisce il campo `data` della risposta.

        Gli errori di business (codice diverso da `000000`) diventano
        `PavoError`, cosi' il chiamante non deve controllare ogni volta.
        Gli errori 5xx si ritentano: sono quasi sempre temporanei.
        """
        url = BASE_API + percorso
        if parametri:
            coda = urllib.parse.urlencode(
                {k: v for k, v in parametri.items() if v is not None})
            if coda:
                url += ("&" if "?" in url else "?") + coda

        dati = None
        if corpo is not None:
            dati = json.dumps(corpo, ensure_ascii=False).encode("utf-8")

        ultimo: Exception | None = None
        for tentativo in range(ritenta):
            richiesta = urllib.request.Request(
                url, data=dati, method=metodo.upper(),
                headers=self.intestazioni(json_body=corpo is not None))
            try:
                with urllib.request.urlopen(richiesta, timeout=self.timeout) as r:
                    self.ultimo_http = r.status
                    testo = r.read().decode("utf-8", "ignore")
                if not testo:
                    return {}
                risposta = json.loads(testo)
                if isinstance(risposta, dict) and "code" in risposta:
                    if risposta.get("code") != ESITO_OK:
                        raise PavoError(
                            risposta.get("message") or "errore Pavo",
                            http=self.ultimo_http,
                            codice=str(risposta.get("code")))
                    return risposta.get("data") or {}
                return risposta
            except urllib.error.HTTPError as e:
                self.ultimo_http = e.code
                corpo_errore = e.read().decode("utf-8", "ignore")[:300]
                ultimo = PavoError(f"HTTP {e.code}: {corpo_errore}", http=e.code)
                if e.code < 500 or tentativo == ritenta - 1:
                    raise ultimo
            except urllib.error.URLError as e:
                ultimo = PavoError(f"rete: {e.reason}")
                if tentativo == ritenta - 1:
                    raise ultimo
            time.sleep(1.5 * (tentativo + 1))
        raise ultimo or PavoError("chiamata fallita")

    def get(self, percorso: str, **parametri) -> Any:
        return self.chiama("GET", percorso, parametri=parametri or None)

    def post(self, percorso: str, corpo: dict | None = None) -> Any:
        return self.chiama("POST", percorso, corpo=corpo)

    def put(self, percorso: str, corpo: dict | None = None) -> Any:
        return self.chiama("PUT", percorso, corpo=corpo)

    def patch(self, percorso: str, corpo: dict | None = None) -> Any:
        return self.chiama("PATCH", percorso, corpo=corpo)

    def delete(self, percorso: str, corpo: dict | None = None) -> Any:
        return self.chiama("DELETE", percorso, corpo=corpo)


import urllib.parse  # noqa: E402  (usato in chiama)


# --- Crediti, modelli, stili -------------------------------------------------

def saldo(p: Pavo) -> dict:
    """Crediti disponibili. `total_balance` e' il numero che conta."""
    return p.get("/subscription/credits-balance")


def costo_stimato(p: Pavo, model_code: str, model_type: str,
                  **extra) -> dict:
    """Costo in crediti di una generazione.

    Usare questo, non `credits/calculate`: `calculate` risponde sempre 0.
    """
    corpo = {"model_code": model_code, "model_type": model_type, **extra}
    return p.post("/aigc/credits/estimate", corpo)


def modelli(p: Pavo) -> list[dict]:
    """I 25 modelli, con `model_type` immagine o video."""
    d = p.get("/pixa/models") or {}
    return d.get("models") or d.get("items") or []


def capacita_modello(p: Pavo, model_code: str, model_type: str) -> dict:
    """Limiti e opzioni di un modello."""
    return p.get("/aigc/model-capability-spec",
                 model_code=model_code, model_type=model_type)


def stili(p: Pavo) -> list[dict]:
    """Gli stili visivi, divisi per categoria (2D, 3D, ...)."""
    d = p.get("/pixa/short-drama/styles") or {}
    return d.get("categories") or []


def strumenti_canvas(p: Pavo) -> dict:
    """Specifiche degli strumenti canvas: audio, immagine, video."""
    return p.get("/pixa/canvas/tool-specs") or {}


def pacchetti_crediti(p: Pavo) -> list[dict]:
    """I pacchetti acquistabili, con prezzo."""
    d = p.get("/subscription/credits-packs") or {}
    return d.get("packs") or []


def transazioni_crediti(p: Pavo, **parametri) -> Any:
    return p.post("/subscription/credits-transactions", parametri or None)


# --- Voci (TTS) --------------------------------------------------------------

def voci(p: Pavo) -> list[dict]:
    """Le voci disponibili. Attenzione: sono tutte in inglese (`locale: en`)."""
    d = p.get("/pixa/short-drama/voices") or {}
    return d.get("voices") or d.get("items") or []


def costo_voce(p: Pavo) -> dict:
    return p.get("/pixa/short-drama/voices/generate/credits") or {}


def genera_voce(p: Pavo, **parametri) -> Any:
    """Genera una voce per un segmento."""
    return p.post("/pixa/short-drama/voices/generate", parametri)


def voce_preferita(p: Pavo, voice_id: str, preferita: bool = True) -> Any:
    if preferita:
        return p.post(f"/pixa/short-drama/voices/{voice_id}/favorite")
    return p.delete(f"/pixa/short-drama/voices/{voice_id}/favorite")


# --- Progetti ----------------------------------------------------------------

def elenca_progetti(p: Pavo, pagina: int = 1, dimensione: int = 20) -> list[dict]:
    """I progetti dell'utente. Il campo identificativo e' `project_id`."""
    d = p.get("/pixa/short-drama/projects",
              page=pagina, page_size=dimensione) or {}
    return d.get("items") or d.get("list") or []


def id_progetto(progetto: dict) -> str:
    """L'id di un progetto, quale che sia il nome del campo.

    L'elenco usa `project_id`; altre risposte usano `id`. Questa funzione
    evita di sbagliare.
    """
    return str(progetto.get("project_id") or progetto.get("id") or "")


def crea_da_copione(p: Pavo, copione: str, titolo: str,
                    ratio: str = "16:9") -> dict:
    """Crea un progetto dal NOSTRO testo. Non lo riscrive.

    E' la via per il controllo totale: l'AI non inventa trama ne' personaggi.
    L'outline esce in italiano.
    """
    return p.post("/pixa/short-drama/projects/paste",
                  {"raw_text": copione, "ratio": ratio, "title": titolo})


def crea_automatico(p: Pavo, requirements: str, titolo: str, sinossi: str,
                    stile: dict, durata: int, drama_type: str = "revenge",
                    voce: str = "female", note: str = "",
                    ratio: str = "16:9") -> dict:
    """Crea un progetto lasciando che l'AI scriva trama e scene.

    Utile solo se non abbiamo un copione nostro: il testo prodotto puo' uscire
    in inglese.
    """
    return p.post("/pixa/short-drama/projects/ai-generate", {
        "requirements": requirements, "title": titolo, "synopsis": sinossi,
        "duration_sec": durata, "ratio": ratio, "style": stile,
        "drama_type": drama_type, "narrator_gender": voce,
        "extra_notes": note,
    })


def carica_progetto(p: Pavo, nome_file: str, contenuto_b64: str) -> dict:
    return p.post("/pixa/short-drama/projects/upload",
                  {"file_name": nome_file, "file_content": contenuto_b64})


def elimina_progetti(p: Pavo, ids: list[str]) -> Any:
    return p.post("/pixa/short-drama/projects/batch-delete", {"project_ids": ids})


def avanzamento(p: Pavo, pid: str) -> dict:
    """Stato di avanzamento di un progetto.

    Le fasi che si fermano ad aspettare una conferma sono quelle che finiscono
    in `_confirming`. Senza conferma il progetto resta fermo per sempre.
    """
    return p.get(f"/pixa/short-drama/projects/{pid}/progress")


# --- Conferme di fase --------------------------------------------------------
# La pipeline si ferma in quattro punti. Ognuno ha il suo endpoint.

CONFERME = {
    "outline": "/outline/confirm",
    "script": "/script/confirm",
    "assets": "/assets/confirm",
    "storyboard": "/storyboard/confirm",
}


def conferma(p: Pavo, pid: str, fase: str) -> Any:
    """Conferma una fase. `fase` e' una di CONFERME."""
    suffisso = CONFERME.get(fase)
    if not suffisso:
        raise PavoError(f"fase sconosciuta: {fase}. Valide: {list(CONFERME)}")
    return p.post(f"/pixa/short-drama/projects/{pid}{suffisso}")


def riprova_fase(p: Pavo, pid: str, fase: str) -> Any:
    """Riprova una fase fallita (outline o episodio)."""
    if fase not in ("outline", "episode"):
        raise PavoError("fase da riprovare: 'outline' o 'episode'")
    return p.post(f"/pixa/short-drama/projects/{pid}/{fase}/retry")


# --- Episodi e storyboard ----------------------------------------------------

def episodi(p: Pavo, pid: str) -> list[dict]:
    """Gli episodi di un progetto, con stato e numero di inquadrature."""
    d = p.get(f"/pixa/short-drama/projects/{pid}/episodes/storyboard") or {}
    return d.get("items") or []


def storyboard(p: Pavo, pid: str) -> dict:
    return p.get(f"/pixa/short-drama/projects/{pid}/episodes/storyboard") or {}


def dettaglio_episodio(p: Pavo, eid: str) -> dict:
    return p.get(f"/pixa/short-drama/episodes/{eid}") or {}


def genera_storyboard(p: Pavo, eid: str) -> Any:
    """Genera i fotogrammi di tutte le inquadrature di un episodio."""
    return p.post(f"/pixa/short-drama/episodes/{eid}/batch/storyboard/generate")


def riprendi_episodio(p: Pavo, pid: str, eid: str) -> Any:
    return p.post(f"/pixa/short-drama/projects/{pid}/episodes/resume",
                  {"episode_id": eid})


def riprova_episodio(p: Pavo, pid: str, eid: str) -> Any:
    return p.post(f"/pixa/short-drama/projects/{pid}/episodes/retry",
                  {"episode_id": eid})


def esporta(p: Pavo, pid: str, eid: str | None = None) -> Any:
    """Prepara l'export del video finale."""
    corpo = {"project_id": pid}
    if eid:
        corpo["episode_id"] = eid
    return p.post("/pixa/short-drama/export", corpo)


# --- Inquadrature (shots) ----------------------------------------------------

def modifica_shot(p: Pavo, shot_id: str, **campi) -> Any:
    """Modifica una inquadratura: prompt, dialogo, durata, ordine."""
    return p.patch("/pixa/short-drama/shots",
                   {"shot_id": shot_id, **campi})


def elimina_shot(p: Pavo, shot_id: str) -> Any:
    return p.delete(f"/pixa/short-drama/shots/{shot_id}")


# --- Segmenti: generazione ---------------------------------------------------

def fotogramma(p: Pavo, segment_id: str, prompt: str, *,
               model_code: str = "seedream4-5",
               style_id: str = "334937235111833600",
               risoluzione: str = "720P",
               riferimento: str | None = None,
               modalita: str = "single_frame") -> Any:
    """Genera il fotogramma di un segmento.

    `riferimento` e' l'URL di una immagine: e' l'unico campo che fa rispettare
    davvero l'aspetto di un luogo o di un personaggio. Descrivere a parole non
    basta: la stessa officina cambia a ogni generazione.
    """
    corpo = {
        "model_code": model_code,
        "style_id": style_id,
        "keyframe_production_mode": modalita,
        "custom_style_prompt": prompt,
        "resolution": risoluzione,
    }
    if riferimento:
        corpo["upload_reference_image_url"] = riferimento
    return p.post(f"/pixa/short-drama/segments/{segment_id}/keyframe/generate",
                  corpo)


def video(p: Pavo, segment_id: str, prompt: str, *,
          model_code: str = "seedance-1-5-pro",
          style_id: str = "334937235111833600",
          risoluzione: str = "720P",
          riferimento: str | None = None,
          modalita: str = "single_frame") -> Any:
    """Genera il video di un segmento. Stessi campi del fotogramma."""
    corpo = {
        "model_code": model_code,
        "style_id": style_id,
        "keyframe_production_mode": modalita,
        "custom_style_prompt": prompt,
        "resolution": risoluzione,
    }
    if riferimento:
        corpo["upload_reference_image_url"] = riferimento
    return p.post(f"/pixa/short-drama/segments/{segment_id}/video/generate",
                  corpo)


def fotogrammi_a_blocchi(p: Pavo, segment_ids: list[str], **opzioni) -> Any:
    """Genera i fotogrammi di piu' segmenti in una volta."""
    return p.post("/pixa/short-drama/segments/batch/keyframe/generate",
                  {"segment_ids": segment_ids, **opzioni})


def video_a_blocchi(p: Pavo, segment_ids: list[str], **opzioni) -> Any:
    return p.post("/pixa/short-drama/segments/batch/video/generate",
                  {"segment_ids": segment_ids, **opzioni})


def riferimenti_segmento(p: Pavo, segment_id: str) -> dict:
    """Le immagini di riferimento allegate a un segmento."""
    return p.get(f"/pixa/short-drama/segments/{segment_id}/reference-images") or {}


def riordina_segmenti(p: Pavo, segment_ids: list[str]) -> Any:
    return p.post("/pixa/short-drama/segments/reorder",
                  {"segment_ids": segment_ids})


def elimina_segmento(p: Pavo, segment_id: str) -> Any:
    return p.delete(f"/pixa/short-drama/segments/{segment_id}")


def aggiungi_shot(p: Pavo, segment_id: str, **campi) -> Any:
    return p.post(f"/pixa/short-drama/segments/{segment_id}/shots", campi)


def media_segmento(p: Pavo, segment_id: str, ruolo: str = "reference") -> Any:
    """I media allegati a un segmento, filtrati per ruolo."""
    return p.get(f"/pixa/short-drama/segments/{segment_id}/media",
                 role=ruolo)


def carica_media_segmento(p: Pavo, segment_id: str, **campi) -> Any:
    return p.post(f"/pixa/short-drama/segments/{segment_id}/media/upload", campi)


# --- Asset: personaggi, oggetti, luoghi --------------------------------------
# Il gruppo decide l'endpoint. `aspect_ratio` e' obbligatorio: senza, il server
# risponde "Invalid parameters".

GRUPPI_ASSET = {
    "character": "/pixa/short-drama/art/characters/generate-cover",
    "prop": "/pixa/short-drama/art/props/generate-image",
    "scene": "/pixa/short-drama/art/scenes/generate-image",
}


def crea_asset(p: Pavo, gruppo: str, nome: str, descrizione: str, *,
               project_id: str = "", entity_id: str = "0",
               modello: str = "seedream4-5", ratio: str = "16:9",
               riferimento: str | None = None) -> Any:
    """Crea un asset e ne genera l'immagine.

    La descrizione e' la stessa che finisce nel prompt, cosi' l'asset e il
    testo non divergono.
    """
    percorso = GRUPPI_ASSET.get(gruppo)
    if not percorso:
        raise PavoError(f"gruppo asset sconosciuto: {gruppo}. "
                        f"Validi: {list(GRUPPI_ASSET)}")
    corpo = {
        "name": nome, "description": descrizione, "image_model": modello,
        "project_id": project_id, "entity_id": entity_id,
        "aspect_ratio": ratio,
    }
    if riferimento:
        corpo["upload_reference_image_url"] = riferimento
    return p.post(percorso, corpo)


def elenca_asset(p: Pavo, gruppo: str, **parametri) -> list[dict]:
    """Gli asset di un gruppo. La chiave della risposta cambia col gruppo:
    `characters`, `props` o `scenes`, non `items`.
    """
    d = p.get("/pixa/short-drama/assets", asset_type=gruppo, **parametri) or {}
    if isinstance(d, dict):
        chiave = {"character": "characters", "prop": "props",
                  "scene": "scenes"}.get(gruppo, "items")
        for k in (chiave, "items", "assets", "list"):
            v = d.get(k)
            if isinstance(v, list):
                return v
    elif isinstance(d, list):
        return d
    return []


def espandi_descrizione(p: Pavo, gruppo: str, descrizione: str) -> str:
    """Allunga una descrizione con l'AI. Puo' uscire in inglese."""
    d = p.post("/pixa/short-drama/art/expand-description",
               {"asset_type": gruppo, "description": descrizione}) or {}
    if isinstance(d, dict):
        return d.get("description") or d.get("text") or ""
    return ""


def vista_quattro(p: Pavo, asset_id: str, **campi) -> Any:
    """Genera le quattro viste di un personaggio (fronte, lati, retro)."""
    return p.post("/pixa/short-drama/art/characters/generate-fourview",
                  {"asset_id": asset_id, **campi})


def versioni_art(p: Pavo, **parametri) -> Any:
    return p.post("/pixa/short-drama/art/art-versions", parametri or None)


def immagini_a_blocchi(p: Pavo, **campi) -> Any:
    return p.post("/pixa/short-drama/art/batch-generate-image", campi)


# --- Attivita' lunghe --------------------------------------------------------

def stato_task(p: Pavo, task_id: str) -> dict:
    return p.get(f"/pixa/short-drama/tasks/{task_id}") or {}


def ferma_task(p: Pavo, task_id: str) -> Any:
    return p.post(f"/pixa/short-drama/tasks/{task_id}/stop")


def conferma_asset(p: Pavo, pid: str) -> Any:
    return p.post(f"/pixa/short-drama/projects/{pid}/assets/confirm")


def finalizza_asset(p: Pavo, pid: str) -> Any:
    return p.post(f"/pixa/short-drama/projects/{pid}/assets/finalize")


# --- Utente e abbonamento ----------------------------------------------------

def profilo(p: Pavo) -> dict:
    return p.get("/user/profile") or {}


def metodi_accesso(p: Pavo) -> Any:
    return p.get("/user/auth_methods")


def aggiorna_profilo(p: Pavo, **campi) -> Any:
    return p.put("/user/profile", campi)


# --- File --------------------------------------------------------------------

def url_caricamento(p: Pavo, nome_file: str, tipo: str = "") -> dict:
    """URL firmato per caricare un file. Serve per le immagini di riferimento."""
    corpo = {"file_name": nome_file}
    if tipo:
        corpo["content_type"] = tipo
    return p.post("/file/presigned-url", corpo)


def ispeziona_file(p: Pavo, url: str) -> Any:
    return p.post("/file/inspect", {"file_url": url})


def da_markdown(p: Pavo, url: str) -> Any:
    return p.post("/file/convert-to-markdown", {"file_url": url})


# --- Canvas (generazione libera) ---------------------------------------------

def migliora_prompt(p: Pavo, prompt: str, **campi) -> Any:
    return p.post("/pixa/canvas/prompt/enhance", {"prompt": prompt, **campi})


def traduci_prompt(p: Pavo, prompt: str, lingua: str = "en") -> Any:
    return p.post("/pixa/canvas/prompt/translate",
                  {"prompt": prompt, "target_language": lingua})


def modelli_canvas(p: Pavo) -> Any:
    return p.get("/pixa/canvas/assets/models")


def annulla_generazione(p: Pavo, **campi) -> Any:
    return p.post("/pixa/canvas/generation/cancel", campi)


def stato_generazione(p: Pavo, **campi) -> Any:
    return p.post("/pixa/canvas/generation/progress", campi)


# --- Alias per compatibilita' con i moduli esistenti -------------------------

def token() -> str:
    """Compatibilita': i moduli esistenti chiamano `token()`."""
    return leggi_token()


def intestazioni(tok: str) -> dict:
    """Compatibilita': header per un token gia' letto."""
    return Pavo(tok).intestazioni(json_body=True)
