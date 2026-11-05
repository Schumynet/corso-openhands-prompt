#!/usr/bin/env python3
"""chat_unica.py — REGOLA DI FERRO: una chat per volta, sempre la stessa.

La regola
---------
Pavo non deve aprire chat nuove a caso. Ogni chat e' una cronologia, e la
cronologia e' il contesto: se si apre una chat nuova per ogni clip, ogni clip
riparte da zero, non ricorda lo stile, non ricorda i personaggi, e il lavoro
precedente resta sparso in decine di conversazioni.

Quindi:

  * **una chat alla volta** per la serie: se ne apre una, le altre non si
    toccano;
  * **le modifiche vanno nella chat che esiste**, mai in una nuova;
  * una chat nuova si apre **solo** se non ne esiste nessuna, o se l'utente lo
    chiede esplicitamente;
  * prima di aprire, si **blocca**: due processi non possono creare due chat
    nello stesso momento.

Come si usa
-----------
    from chat_unica import chat_della_serie, con_chat, registra

    # La chat della serie: la prende se c'e', la crea solo se manca.
    chat = chat_della_serie("vento_in_curva", titolo="Vento in Curva")

    # Lavorare dentro quella chat, con il blocco attivo.
    with con_chat("vento_in_curva", titolo="Vento in Curva") as chat_id:
        ...  # genera, modifica, rileggi

Da riga di comando:

    python3 chat_unica.py --mostra                  # elenca le chat
    python3 chat_unica.py --apri vento_in_curva     # apre solo se manca
    python3 chat_unica.py --verifica                # controlla la regola
"""

from __future__ import annotations

import argparse
import fcntl
import json
import time
from contextlib import contextmanager
from pathlib import Path

BASE = Path(__file__).resolve().parent
REGISTRO = BASE / "chat_registro.json"
BLOCCO = BASE / ".chat_unica.lock"

# Quante chat puo' avere una serie. Una: e' la regola.
MAX_CHAT_PER_SERIE = 1


class RegolaViolata(RuntimeError):
    """Si e' tentato di aprire una chat quando non si doveva."""


# --- Registro ----------------------------------------------------------------
# Tiene, per ogni serie, la chat di riferimento e la cronologia delle aperture.
# Serve a sapere se una chat esiste gia' senza interrogare il server ogni volta.

def _leggi_registro() -> dict:
    if not REGISTRO.is_file():
        return {}
    try:
        return json.loads(REGISTRO.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def _scrivi_registro(dati: dict):
    REGISTRO.write_text(json.dumps(dati, ensure_ascii=False, indent=2),
                        encoding="utf-8")


def _blocca():
    """Blocco esclusivo: due processi non creano due chat insieme."""
    BLOCCO.parent.mkdir(parents=True, exist_ok=True)
    f = open(BLOCCO, "w")
    fcntl.flock(f, fcntl.LOCK_EX)
    return f


def _sblocca(f):
    try:
        fcntl.flock(f, fcntl.LOCK_UN)
        f.close()
    except Exception:
        pass


# --- Il registro delle chat --------------------------------------------------

def chat_registrata(serie: str) -> str | None:
    """L'id della chat di una serie, se e' gia' stata aperta."""
    voce = (_leggi_registro().get(serie) or {})
    return str(voce["conversation_id"]) if voce.get("conversation_id") else None


def tutte_le_chat() -> dict:
    return _leggi_registro()


def registra(serie: str, conversation_id: str, titolo: str = "",
             origine: str = "", _gia_bloccato: bool = False) -> dict:
    """Scrive una chat nel registro, marcandola come quella della serie.

    `_gia_bloccato` serve ai chiamanti che tengono gia' il blocco: `flock` non
    e' rientrante, e riprenderlo bloccherebbe il processo per sempre.
    """
    blocco = None if _gia_bloccato else _blocca()
    try:
        dati = _leggi_registro()
        voce = dati.setdefault(serie, {})
        if voce.get("conversation_id") and \
                str(voce["conversation_id"]) != str(conversation_id):
            raise RegolaViolata(
                f"la serie '{serie}' ha gia' la chat "
                f"{voce['conversation_id']}; non ne apro un'altra "
                f"({conversation_id}). Se serve davvero, usa --sostituisci.")
        voce.update({
            "conversation_id": str(conversation_id),
            "titolo": titolo or voce.get("titolo", ""),
            "origine": origine or voce.get("origine", ""),
            "aperta_il": voce.get("aperta_il") or time.strftime("%Y-%m-%d %H:%M"),
            "ultimo_uso": time.strftime("%Y-%m-%d %H:%M"),
        })
        _scrivi_registro(dati)
        return voce
    finally:
        if blocco is not None:
            _sblocca(blocco)


def _tocca(serie: str):
    dati = _leggi_registro()
    if serie in dati:
        dati[serie]["ultimo_uso"] = time.strftime("%Y-%m-%d %H:%M")
        _scrivi_registro(dati)


# --- Apertura, con la regola ------------------------------------------------

def _crea_chat(titolo: str) -> str:
    """Apre davvero una conversazione su Pavo. Una sola, quando serve."""
    import pavo_api as A

    p = A.Pavo()
    d = p.post("/chat/conversation", {
        "title": json.dumps([{"type": "text", "content": titolo[:80]}]),
        "folder_id": "",
        "kb_strict": False,
    })
    chat = str(d.get("conversation_id") or "")
    if not chat:
        raise RegolaViolata(f"il server non ha restituito un id: {d}")
    return chat


def chat_della_serie(serie: str, titolo: str = "", forza_nuova: bool = False,
                     _gia_bloccato: bool = False) -> str:
    """La chat della serie. La crea solo se manca.

    `forza_nuova` esiste per il caso esplicito: l'utente ha chiesto una chat
    nuova. Senza, la regola vale sempre.

    `_gia_bloccato` serve a `con_chat`, che tiene gia' il blocco: riprenderlo
    sullo stesso file bloccherebbe il processo per sempre, perche' `flock` non
    e' rientrante fra descrittori diversi.
    """
    blocco = None if _gia_bloccato else _blocca()
    try:
        esistente = chat_registrata(serie)
        if esistente and not forza_nuova:
            _tocca(serie)
            return esistente

        if esistente and forza_nuova:
            # Non si cancella la vecchia: si tiene traccia che e' stata
            # sostituita, cosi' la cronologia non va persa.
            dati = _leggi_registro()
            v = dati.get(serie, {})
            v.setdefault("sostituite", []).append(v.get("conversation_id"))
            _scrivi_registro(dati)

        chat = _crea_chat(titolo or f"Vento in Curva — {serie}")
        registra(serie, chat, titolo, origine="creata", _gia_bloccato=True)
        return chat
    finally:
        if blocco is not None:
            _sblocca(blocco)


@contextmanager
def con_chat(serie: str, titolo: str = ""):
    """Lavora dentro la chat della serie, tenendo il blocco per tutta l'opera.

    Il blocco serve: mentre una generazione e' in corso, un altro processo non
    deve aprire una chat nuova ne' scrivere nella stessa. Si aspetta.
    """
    blocco = _blocca()
    try:
        chat = chat_della_serie(serie, titolo, _gia_bloccato=True)
        _tocca(serie)
        yield chat
    finally:
        _sblocca(blocco)


# --- Verifica ----------------------------------------------------------------

def verifica() -> dict:
    """Controlla che la regola sia rispettata, e lo dice in chiaro."""
    dati = _leggi_registro()
    problemi = []
    for serie, voce in dati.items():
        n = 1 if voce.get("conversation_id") else 0
        n += len(voce.get("sostituite") or [])
        if n > MAX_CHAT_PER_SERIE and not voce.get("sostituite"):
            problemi.append(
                f"serie '{serie}': {n} chat attive, il massimo e' "
                f"{MAX_CHAT_PER_SERIE}")
    return {
        "regola": f"massimo {MAX_CHAT_PER_SERIE} chat per serie",
        "serie_registrate": len(dati),
        "rispettata": not problemi,
        "problemi": problemi,
        "chat": {s: v.get("conversation_id") for s, v in dati.items()},
    }


def storico(serie: str) -> list[dict]:
    """I turni della chat: serve a riprendere il lavoro senza ripartire da zero."""
    import pavo_api as A

    chat = chat_registrata(serie)
    if not chat:
        return []
    p = A.Pavo()
    d = p.get("/chat/conversation/history", conversation_id=chat) or {}
    return d.get("turns") or []


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Regola di ferro: una chat per volta, sempre la stessa.")
    ap.add_argument("--mostra", action="store_true",
                    help="elenca le chat registrate")
    ap.add_argument("--apri", metavar="SERIE",
                    help="apre la chat della serie, solo se manca")
    ap.add_argument("--titolo", default="", help="titolo, se si apre")
    ap.add_argument("--nuova", action="store_true",
                    help="apre davvero una chat nuova (sostituisce la vecchia)")
    ap.add_argument("--verifica", action="store_true",
                    help="controlla che la regola sia rispettata")
    ap.add_argument("--storico", metavar="SERIE",
                    help="mostra i turni della chat")
    args = ap.parse_args()

    if args.mostra or (not args.apri and not args.verifica and not args.storico):
        dati = tutte_le_chat()
        if not dati:
            print("nessuna chat registrata.")
            print("La prima si apre con:  python3 chat_unica.py --apri vento_in_curva")
            return 0
        print(f"{len(dati)} serie registrate:")
        for s, v in dati.items():
            sost = v.get("sostituite") or []
            print(f"  {s:22} chat {v.get('conversation_id')}  "
                  f"aperta {v.get('aperta_il')}  usata {v.get('ultimo_uso')}"
                  + (f"  ({len(sost)} sostituite)" if sost else ""))
        return 0

    if args.verifica:
        print(json.dumps(verifica(), ensure_ascii=False, indent=2))
        return 0

    if args.storico:
        turni = storico(args.storico)
        print(f"{len(turni)} turni nella chat di '{args.storico}'")
        for t in turni[:10]:
            prompt = str(t.get("prompt") or t.get("content") or "")[:70]
            print(f"  {prompt}")
        return 0

    if args.apri:
        esistente = chat_registrata(args.apri)
        if esistente and not args.nuova:
            print(f"la chat di '{args.apri}' esiste gia': {esistente}")
            print("La regola non ne apre un'altra. Per una nuova davvero: --nuova")
            return 0
        chat = chat_della_serie(args.apri, args.titolo, forza_nuova=args.nuova)
        azione = "sostituita con" if esistente else "aperta"
        print(f"chat {azione}: {chat}")
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
