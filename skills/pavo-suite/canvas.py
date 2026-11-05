"""Pavo AI - modalita' Canvas.

Il Canvas e' il generatore libero di Pavo: si lavora per nodi (testo, immagine,
video, audio) invece che dentro un progetto a copione. Ogni nodo ha modelli e
vincoli propri, e i vincoli si leggono dall'app invece di indovinarli.

Le specifiche stanno in `canvas_specs.json`, copiate da `GET /pixa/canvas/tool-specs`.
Per aggiornarle: `python3 canvas.py --aggiorna`.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
SPECS = HERE / "canvas_specs.json"

_cache: dict | None = None


def specifiche(ricarica: bool = False) -> dict:
    """Le specifiche Canvas salvate. Le tiene in memoria dopo la prima lettura."""
    global _cache
    if _cache is None or ricarica:
        _cache = json.loads(SPECS.read_text(encoding="utf-8"))
    return _cache


# --- consultazione -----------------------------------------------------------

def modelli(tipo: str) -> list[dict]:
    """I modelli di un nodo. `tipo` e' 'video', 'immagine', 'audio' o 'testo'."""
    chiave = {"testo": "modelli_testo", "text": "modelli_testo"}.get(tipo, tipo)
    return specifiche().get(chiave, [])


def modello(codice: str) -> dict | None:
    """Un modello per codice, cercato in tutti i tipi."""
    for tipo in ("video", "immagine", "audio", "modelli_testo"):
        for m in specifiche().get(tipo, []):
            if m.get("codice") == codice:
                return m
    return None


def stili(come: str = "lista") -> Any:
    """I 60 stili del Canvas. `come='mappa'` da' {codice: nome}."""
    voci = specifiche().get("stili", [])
    if come == "mappa":
        return {s["codice"]: s["nome"] for s in voci}
    return voci


def guide(tipo: str | None = None) -> Any:
    """Le guide ufficiali, con i prompt che usa Pavo stesso.

    Sono utili perche' mostrano come formula le richieste chi ha costruito
    l'app: copiarne la forma funziona meglio che inventarne una.
    """
    g = specifiche().get("guide", {})
    return g if tipo is None else g.get(tipo, [])


# --- validazione -------------------------------------------------------------

def valida(tipo: str, codice: str, *, durata: int | None = None,
           rapporto: str | None = None, risoluzione: str | None = None,
           modo: str | None = None, immagini: int | None = None,
           caratteri: int | None = None) -> list[str]:
    """Controlla i parametri contro i vincoli del modello. Vuoto = tutto bene.

    Serve a non spendere crediti su una richiesta che il server rifiuterebbe.
    """
    m = modello(codice)
    if not m:
        return [f"modello '{codice}' inesistente"]
    if not m.get("online"):
        return [f"'{codice}' non e' disponibile in questo momento"]

    errori: list[str] = []

    if durata is not None:
        durate = m.get("durate") or []
        if durate and durata not in durate:
            errori.append(f"durata {durata}s non ammessa (scegli tra {min(durate)}-{max(durate)}s)")
    if rapporto is not None:
        rapporti = m.get("rapporti") or []
        if rapporti and rapporto not in rapporti:
            errori.append(f"rapporto '{rapporto}' non ammesso (scegli tra {', '.join(rapporti)})")
    if risoluzione is not None:
        ris = m.get("risoluzioni") or []
        if ris and risoluzione not in ris:
            errori.append(f"risoluzione '{risoluzione}' non ammessa (scegli tra {', '.join(ris)})")
    if modo is not None:
        modi = m.get("modi") or []
        if modi and modo not in modi:
            errori.append(f"modo '{modo}' non ammesso (scegli tra {', '.join(modi)})")
    if immagini is not None:
        massimo = m.get("max_immagini")
        if massimo is not None and immagini > massimo:
            errori.append(f"{immagini} immagini, ma il massimo e' {massimo}")
    if caratteri is not None:
        massimo = m.get("max_caratteri")
        if massimo is not None and caratteri > massimo:
            errori.append(f"{caratteri} caratteri, ma il massimo e' {massimo}")

    return errori


def compatibili(tipo: str, modo: str) -> list[str]:
    """I codici dei modelli che supportano un certo modo."""
    return [m["codice"] for m in modelli(tipo) if modo in (m.get("modi") or [])]


# --- prompt ------------------------------------------------------------------

def migliora(p: Any, testo: str) -> str:
    """Riscrive un prompt in inglese, piu' dettagliato.

    Il campo e' `items`: una lista di oggetti con discriminatore `type` e il
    testo in `content`. Sembra strano ma e' cosi': `prompt` da solo risponde
    400 'Items is required'.
    """
    r = p.post("/pixa/canvas/prompt/enhance",
               {"items": [{"type": "text", "content": testo}]})
    voci = r.get("items") or []
    return voci[0].get("content", "") if voci else r.get("text", "")


def traduci(p: Any, testo: str) -> str:
    """Traduce verso il cinese.

    ATTENZIONE: `target_language` viene ignorato. La direzione e' sempre
    `en2zh` qualunque cosa si passi, anche 'en'. Il campo `direction` nella
    risposta lo conferma. Non fidarti del nome del parametro.
    """
    r = p.post("/pixa/canvas/prompt/translate",
               {"items": [{"type": "text", "content": testo}]})
    return r.get("text", "")


def espandi(p: Any, url_immagine: str, tipo_asset: str = "character") -> str:
    """Da un'immagine ricava la descrizione testuale. Vuole l'immagine, non il testo."""
    r = p.post("/pixa/canvas/assets/description/expand",
               {"asset_type": tipo_asset, "image_url": url_immagine})
    return r.get("description") or r.get("text") or ""


# --- generazione -------------------------------------------------------------

def annulla(p: Any, **campi) -> Any:
    """Annulla una generazione in corso."""
    return p.post("/pixa/canvas/generation/cancel", campi)


def avanzamento(p: Any, **campi) -> Any:
    """Lo stato di una generazione. Il Canvas lavora in asincrono."""
    return p.post("/pixa/canvas/generation/progress", campi)


# --- asset -------------------------------------------------------------------

def crea_asset(p: Any, tipo: str, **campi) -> Any:
    """Crea un asset. `tipo` e' 'characters', 'scenes' o 'props'.

    Gli asset sono cio' che rende coerenti i personaggi e i luoghi tra una clip
    e l'altra: nel prompt si richiamano con @.
    """
    if tipo not in ("characters", "scenes", "props"):
        raise ValueError("tipo deve essere 'characters', 'scenes' o 'props'")
    return p.post(f"/pixa/canvas/assets/{tipo}", campi)


def quattro_viste(p: Any, **campi) -> Any:
    """Genera il foglio a quattro viste di un personaggio.

    E' il modo per fissare l'aspetto di un personaggio: frontale, profilo,
    retro e tre quarti. Poi lo si richiama con @ nel prompt del video.
    """
    return p.post("/pixa/canvas/assets/characters/generate-fourview", campi)


# --- riga di comando ---------------------------------------------------------

def _aggiorna() -> int:
    sys.path.insert(0, str(HERE))
    import pavo_api as A

    grezze = A.strumenti_canvas(A.Pavo())
    vecchie = specifiche()
    nuove = dict(vecchie)
    nuove["versione"] = grezze.get("version")

    def modello_pulito(it: dict, gruppo: str) -> dict:
        s = it.get("capability_spec") or {}
        out = {"codice": s.get("model_code"), "nome": it.get("display_name"),
               "online": s.get("is_online")}
        b = s.get(gruppo) or {}
        out["modi"] = b.get("mode_types") or []
        if gruppo == "video":
            out["durate"] = b.get("duration_seconds") or []
            out["rapporti"] = b.get("aspect_ratios") or []
            out["risoluzioni"] = sorted({p.get("resolution")
                                         for p in (b.get("output_profiles") or [])
                                         if p.get("resolution")})
            ii = b.get("input_image") or {}
            out["ruoli_immagine"] = ii.get("supported_roles") or []
            out["max_immagini"] = ii.get("max_count")
            out["max_caratteri"] = (b.get("input_text") or {}).get("max_length")
        elif gruppo == "image":
            out["rapporti"] = b.get("aspect_ratios") or []
        else:
            out["max_caratteri"] = (b.get("input_text") or {}).get("max_length")
        return out

    mo = grezze.get("modelOptions") or {}
    for chiave, gruppo in (("canvas_video", "video"), ("canvas_image", "immagine"),
                           ("canvas_audio", "audio")):
        items = (mo.get(chiave) or {}).get("items") or []
        nuove[gruppo] = [modello_pulito(i, gruppo) for i in items]

    sc = (grezze.get("styleCatalog") or {}).get("options") or []
    if sc:
        nuove["stili"] = [{"codice": s.get("code"), "nome": s.get("name")} for s in sc]

    nc = grezze.get("nodeConfigs") or {}
    nuove["guide"] = {}
    for tipo, lista in (nc.get("guide") or {}).items():
        voci = []
        for v in lista:
            pr = None
            for n in (v.get("extra") or {}).get("node_list", []):
                pr = (((n.get("data") or {}).get("params")) or {}).get("prompt") or pr
            voci.append({"codice": v.get("code"), "nome": v.get("name"),
                         "prompt_ufficiale": pr})
        nuove["guide"][tipo] = voci

    SPECS.write_text(json.dumps(nuove, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"aggiornato: {SPECS.name}  versione {nuove['versione']}")
    print(f"  video {len(nuove.get('video', []))} | immagine {len(nuove.get('immagine', []))}"
          f" | audio {len(nuove.get('audio', []))} | stili {len(nuove.get('stili', []))}")
    return 0


def _main(argv: list[str]) -> int:
    if "--aggiorna" in argv:
        return _aggiorna()
    if "--modelli" in argv:
        tipo = argv[argv.index("--modelli") + 1] if len(argv) > argv.index("--modelli") + 1 else "video"
        for m in modelli(tipo):
            stato = "" if m.get("online") else "  (offline)"
            print(f"  {m['codice']:22} {m.get('nome','')}{stato}")
        return 0
    if "--stili" in argv:
        for s in stili():
            print(f"  {s['codice']:34} {s['nome']}")
        return 0
    if "--guide" in argv:
        for tipo, voci in guide().items():
            print(f"{tipo}:")
            for v in voci:
                print(f"  [{v['codice']}] {v.get('nome','')}")
                if v.get("prompt_ufficiale"):
                    print(f"      {v['prompt_ufficiale'][:110]}")
        return 0
    if "--compatibili" in argv:
        i = argv.index("--compatibili")
        tipo, modo = argv[i + 1], argv[i + 2]
        for c in compatibili(tipo, modo):
            print(" ", c)
        return 0
    print(__doc__)
    print("comandi: --aggiorna | --modelli <tipo> | --stili | --guide | --compatibili <tipo> <modo>")
    return 0


if __name__ == "__main__":
    raise SystemExit(_main(sys.argv[1:]))
