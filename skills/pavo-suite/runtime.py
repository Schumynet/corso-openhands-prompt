#!/usr/bin/env python3
"""runtime.py — ricava da se' l'indirizzo pubblico del runtime.

Il problema
-----------
L'indirizzo pubblico di questo ambiente contiene il nome del sandbox:

    https://work-1-<sandbox>.prod-runtime.all-hands.dev

Il nome cambia con l'account. Se lo si scrive dentro il codice, il pacchetto
funziona su un account e si rompe su tutti gli altri. Questo modulo lo ricava
al momento dell'uso, con piu' ripieghi in ordine di affidabilita'.

Da dove viene il nome
---------------------
1. `PAVO_RUNTIME_HOST` — se impostata, vince. Serve a forzarla a mano.
2. `SANDBOX_ID` — la variabile che usa l'automazione di OpenHands.
3. `HOSTNAME` — dentro il pod ha la forma `runtime-<sandbox>-<hash>-<hash>`.
4. Un file `.runtime_host`, scritto dalla skill la prima volta che indovina.
5. Ultimo ripiego: il valore noto dell'ambiente di sviluppo.

Uso
---
    from runtime import host_pubblico, url_pubblico, porta

    host_pubblico()          # 'dsczaeegyhjtbats.prod-runtime.all-hands.dev'
    url_pubblico(12000)      # 'https://work-1-.../vnc.html'
    url_base(12000)          # 'https://work-1-...'

Da riga di comando:

    python3 runtime.py            # mostra host e indirizzi
    python3 runtime.py --scrivi   # salva l'host in .runtime_host
"""

from __future__ import annotations

import argparse
import os
import re
from pathlib import Path

BASE = Path(__file__).resolve().parent
FILE_HOST = BASE / ".runtime_host"

DOMINIO = "prod-runtime.all-hands.dev"
HOST_NOTO = "dsczaeegyhjtbats.prod-runtime.all-hands.dev"

# I due host pubblici, per numero di porta. OpenHands ne espone due.
HOST_PUBBLICO_1 = "work-1"
HOST_PUBBLICO_2 = "work-2"
PORTE_NOTE = {12000: HOST_PUBBLICO_1, 12001: HOST_PUBBLICO_2}


def nome_sandbox() -> str | None:
    """Il nome del sandbox, ricavato con il ripiego piu' affidabile."""
    for variabile in ("PAVO_SANDBOX", "SANDBOX_ID"):
        valore = (os.environ.get(variabile) or "").strip()
        if valore:
            return valore

    # Dentro il pod: `runtime-<sandbox>-<hash>-<hash>`.
    hostname = (os.environ.get("HOSTNAME") or "").strip()
    m = re.match(r"runtime-([a-z0-9]+)-", hostname)
    if m:
        return m.group(1)

    if FILE_HOST.is_file():
        valore = FILE_HOST.read_text(encoding="utf-8").strip()
        if valore:
            return valore

    return None


def host_pubblico() -> str:
    """Il dominio pubblico, senza schema.

    Prima `PAVO_RUNTIME_HOST`, che vince su tutto: serve a forzarlo quando la
    ricostruzione automatica non e' possibile.
    """
    forzato = (os.environ.get("PAVO_RUNTIME_HOST") or "").strip()
    if forzato:
        return forzato.replace("https://", "").replace("http://", "").rstrip("/")

    nome = nome_sandbox()
    if nome:
        return f"{nome}.{DOMINIO}"

    return HOST_NOTO


def host_work(porta: int = 12000) -> str:
    """Quale dei due host pubblici corrisponde a una porta.

    12000 e 12001 sono i due host esposti. Le altre porte passano dal primo.
    """
    return PORTE_NOTE.get(porta, HOST_PUBBLICO_1)


def url_base(porta: int = 12000) -> str:
    return f"https://{host_work(porta)}-{host_pubblico()}"


def url_pubblico(porta: int = 12000, percorso: str = "") -> str:
    percorso = percorso.lstrip("/")
    return f"{url_base(porta)}/{percorso}" if percorso else url_base(porta)


def scrivi_file_host():
    """Salva l'host indovinato, cosi' i prossimi avvii sono immediati."""
    valore = host_pubblico()
    FILE_HOST.write_text(valore + "\n", encoding="utf-8")
    return valore


def verifica() -> dict:
    return {
        "host": host_pubblico(),
        "sandbox": nome_sandbox() or "(non ricavato, uso il valore noto)",
        "hostname": os.environ.get("HOSTNAME", ""),
        "file_host": str(FILE_HOST) if FILE_HOST.is_file() else "(assente)",
        "forzato": bool(os.environ.get("PAVO_RUNTIME_HOST")),
        "url_12000": url_base(12000),
        "url_12001": url_base(12001),
    }


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Ricava l'indirizzo pubblico del runtime.")
    ap.add_argument("--scrivi", action="store_true",
                    help="salva l'host indovinato in .runtime_host")
    args = ap.parse_args()

    if args.scrivi:
        print(f"salvato: {scrivi_file_host()}")
        return 0

    v = verifica()
    print(f"  host pubblico : {v['host']}")
    print(f"  sandbox       : {v['sandbox']}")
    print(f"  hostname pod  : {v['hostname']}")
    print(f"  forzato a mano: {'si' if v['forzato'] else 'no'}")
    print(f"  porta 12000   : {v['url_12000']}")
    print(f"  porta 12001   : {v['url_12001']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
