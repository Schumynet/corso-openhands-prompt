#!/usr/bin/env python3
"""correzioni.py — guardie riutilizzabili per i prompt video.

Il problema
-----------
Nella generazione video lo stesso errore torna. Un soggetto che finisce fuori
dal veicolo, un'azione che non si capisce, una scena ferma dove serviva
movimento. Se ogni volta si riscrive la correzione a mano, si corregge la stessa
cosa dieci volte, e alla clip dopo l'errore e' tornato.

La soluzione
------------
Ogni errore trovato in revisione diventa una **guardia**: un testo che viene
iniettato nel prompt quando il trigger scatta. Il trigger e' una parola chiave
nell'azione della clip. Cosi' l'errore non si ripete, nemmeno negli episodi
futuri.

Le guardie non sono scritte qui dentro: stanno in `correzioni.json`. Questo
modulo e' il motore che le applica. Cosi' si adatta a qualsiasi progetto, non
solo a uno.

Uso
---
    from correzioni import applica, guardie_per_clip

    # Le guardie che valgono per questa clip.
    testo = applica("il pilota guida il kart verso la curva", clip=7)

    # Solo la lista, se serve comporla diversamente.
    lista = guardie_per_clip("il pilota guida il kart", clip=7)

Da riga di comando:

    python3 correzioni.py --prova "il pilota guida verso la curva"
    python3 correzioni.py --elenca
    python3 correzioni.py --valida

Formato di `correzioni.json`
----------------------------
{
  "guardie": [
    {
      "nome": "orientamento",
      "testo": "Il testo da iniettare nel prompt...",
      "trigger": ["kart", "pista", "curva"],     // basta una parola
      "luoghi": ["pista"],                        // oppure un luogo
      "non_trigger": ["fermo"],                   // se presente, non scatta
      "richiede_gesto": false                     // vedi sotto
    }
  ]
}

`richiede_gesto` serve alla guardia "azione leggibile": scatta solo se
l'azione NON contiene gia' un gesto forte. Un gesto forte e' un verbo che si
vede: stringe, porge, spinge, monta, corre, apre.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
CONFIG = BASE / "correzioni.json"

# Verbi che rendono l'azione gia' leggibile. Se l'azione ne contiene uno, la
# guardia "azione leggibile" non serve: c'e' gia' qualcosa che si vede.
GESTI_FORTI = (
    "stringe", "porge", "spinge", "china", "monta", "smonta", "corre",
    "tocca", "infila", "esce", "entra", "apre", "chiude", "lancia", "afferra",
    "guida", "sbanda", "scivola", "sorpassa", "insegue", "scatta", "sfreccia",
    "supera", "accelera", "frena", "salta", "colpisce", "solleva", "tira",
    "spinge", "gira", "si volta", "raccoglie", "posa", "sistema",
)


def carica_config(percorso: Path | None = None) -> dict:
    """Le guardie dal file di configurazione."""
    p = percorso or CONFIG
    if not p.is_file():
        return {"guardie": []}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ValueError(f"{p} non e' un JSON valido: {e}") from e


def guardie_per_clip(azione: str, clip: int | None = None,
                     luoghi: dict | None = None,
                     senza_personaggi: set | None = None,
                     config: dict | None = None) -> list[str]:
    """Le guardie da iniettare nel prompt di questa clip.

    `luoghi` e' la mappa clip -> luogo. `senza_personaggi` e' l'insieme delle
    clip di solo ambiente. Entrambi sono facoltativi: senza, si usano solo le
    parole chiave.
    """
    cfg = config or carica_config()
    testo = (azione or "").lower()
    luogo = None
    if luoghi and clip is not None:
        luogo = luoghi.get(clip)

    fuori: list[str] = []
    for g in cfg.get("guardie", []):
        # Una guardia con "luoghi" vale solo in quei luoghi; una senza vale
        # sempre, se scatta il trigger.
        luoghi_g = g.get("luoghi")
        if luoghi_g and (luogo not in luoghi_g):
            # Il luogo puo' anche essere dedotto dal testo, se la guardia lo
            # prevede con "luoghi_da_testo".
            da_testo = g.get("luoghi_da_testo") or []
            if not (da_testo and any(k in testo for k in da_testo)):
                continue

        # Le clip di solo ambiente: guardia dedicata, senza trigger.
        if g.get("solo_senza_personaggi"):
            if clip is not None and senza_personaggi and clip in senza_personaggi:
                fuori.append(g["testo"])
            continue

        trigger = g.get("trigger") or []
        if trigger and not any(k in testo for k in trigger):
            continue

        non_trigger = g.get("non_trigger") or []
        if non_trigger and any(k in testo for k in non_trigger):
            continue

        if g.get("richiede_gesto"):
            if any(k in testo for k in GESTI_FORTI):
                continue

        fuori.append(g["testo"])

    return fuori


def applica(azione: str, clip: int | None = None, luoghi: dict | None = None,
            senza_personaggi: set | None = None,
            config: dict | None = None, separatore: str = " ") -> str:
    """L'azione con le guardie iniettate.

    Le guardie vanno **dopo** l'azione: il modello legge prima cosa deve
    mostrare, poi i vincoli. Il contrario confonde le priorita'.
    """
    guardie = guardie_per_clip(azione, clip, luoghi, senza_personaggi, config)
    if not guardie:
        return azione
    return separatore.join([azione.rstrip("."), *guardie])


def valida(config: dict | None = None) -> dict:
    """Controlla che le guardie siano scritte bene."""
    cfg = config or carica_config()
    problemi: list[str] = []
    nomi: list[str] = []
    for i, g in enumerate(cfg.get("guardie", [])):
        nome = g.get("nome") or f"(senza nome, posizione {i})"
        nomi.append(nome)
        if not g.get("testo"):
            problemi.append(f"{nome}: manca il testo")
        ha_condizione = bool(g.get("trigger") or g.get("luoghi")
                             or g.get("solo_senza_personaggi"))
        if not ha_condizione:
            problemi.append(
                f"{nome}: non ha nessuna condizione, scatterebbe sempre")
    doppi = [n for n in set(nomi) if nomi.count(n) > 1 and n]
    if doppi:
        problemi.append(f"nomi ripetuti: {', '.join(sorted(doppi))}")
    return {"guardie": len(nomi), "nomi": nomi, "problemi": problemi,
            "valido": not problemi}


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Guardie di correzione per i prompt video.")
    ap.add_argument("--prova", metavar="AZIONE",
                    help="mostra le guardie per questa azione")
    ap.add_argument("--clip", type=int, help="numero della clip")
    ap.add_argument("--elenca", action="store_true", help="elenca le guardie")
    ap.add_argument("--valida", action="store_true",
                    help="controlla il file di configurazione")
    ap.add_argument("--config", help="percorso di correzioni.json")
    args = ap.parse_args()

    cfg = carica_config(Path(args.config) if args.config else None)

    if args.valida:
        r = valida(cfg)
        print(f"guardie: {r['guardie']}   valido: {r['valido']}")
        for n in r["nomi"]:
            print(f"  {n}")
        for p in r["problemi"]:
            print(f"  PROBLEMA: {p}")
        return 0 if r["valido"] else 1

    if args.elenca:
        for g in cfg.get("guardie", []):
            trig = ", ".join(g.get("trigger") or []) or "(nessun trigger)"
            print(f"  {g.get('nome','?'):22} trigger: {trig[:60]}")
        return 0

    if args.prova:
        print(applica(args.prova, clip=args.clip, config=cfg))
        return 0

    print(f"configurazione: {CONFIG}")
    print(json.dumps(valida(cfg), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
