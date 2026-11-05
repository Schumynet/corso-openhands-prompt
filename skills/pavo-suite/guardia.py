#!/usr/bin/env python3
"""guardia.py — REGOLA DI FERRO: il browser tiene una sola pagina, e non apre
nient'altro da solo.

La regola
---------
Il browser di sessione serve a **una cosa sola**: stare su
`https://app.pavo-ai.work/` mentre fai il login. Non deve aprire chat, non deve
aprire altre schede, non deve navigare altrove.

Questa guardia la applica, invece di limitarsi a raccomandarla:

  * se compare una scheda che non e' la pagina dell'app, la **chiude**;
  * se la pagina dell'app finisce su una URL di chat o su un altro sito, la
    **riporta** su `app.pavo-ai.work`;
  * se una scheda apre una finestra nuova, la **chiude**;
  * tiene sempre **una sola** scheda dell'app.

Cosa NON fa
-----------
Non tocca la pagina di login: li' devi poter cliccare "Accedi con Google",
scegliere l'account, e passare eventualmente per `accounts.google.com`. Quelle
sono navigazioni legittime del login. La guardia interviene solo su schede
**in piu'** e su navigazioni **fuori dal percorso di accesso**.

Uso da riga di comando:
    python3 guardia.py --cdp 9222 --una-passo
    python3 guardia.py --cdp 9222 --controlla        # un solo giro, per diagnosi

Uso come modulo:
    from guardia import Guardia
    g = Guardia(cdp=9222)
    g.applica()          # un giro: chiude le schede di troppo
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.request

APP_HOST = "app.pavo-ai.work"
URL_APP = "https://app.pavo-ai.work/"

# Host che fanno parte del percorso di accesso: la guardia li lascia stare.
# Senza questa lista, il login con Google si romperebbe.
HOST_ACCESSO = (
    "app.pavo-ai.work",
    "accounts.google.com",
    "accounts.youtube.com",
    "apis.google.com",
    "content-accounts.google.com",
    "ssl.gstatic.com",
    "www.google.com",
    "google.com",
)

# Percorsi dell'app che la guardia non vuole vedere aperti da soli. La chat e'
# il caso principale: non deve aprirsi random.
PERCORSI_VIETATI = ("/chat", "/im/", "/group_chat", "/support")


def _leggi_json(url: str, timeout: int = 5):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "ignore"))


def _invia(cdp: int, percorso: str, metodo: str = "GET") -> dict:
    richiesta = urllib.request.Request(f"http://localhost:{cdp}{percorso}",
                                       method=metodo)
    with urllib.request.urlopen(richiesta, timeout=8) as r:
        corpo = r.read().decode("utf-8", "ignore")
    return json.loads(corpo) if corpo.strip() else {}


class Guardia:
    """Applica la regola di ferro su un browser collegato via CDP."""

    def __init__(self, cdp: int = 9222, verboso: bool = True):
        self.cdp = cdp
        self.verboso = verboso
        self.chiusi = 0
        self.riportati = 0

    def _log(self, messaggio: str):
        if self.verboso:
            print(f"  [guardia] {messaggio}", flush=True)

    def schede(self) -> list[dict]:
        try:
            return [s for s in _leggi_json(f"http://localhost:{self.cdp}/json")
                    if s.get("type") == "page"]
        except Exception:
            return []

    def _chiudi(self, scheda: dict):
        """Chiude una scheda. Preferisce l'endpoint HTTP, che non ha bisogno
        di websocket."""
        try:
            _invia(self.cdp, f"/json/close/{scheda['id']}")
            self.chiusi += 1
            return True
        except Exception:
            return False

    def _riporta(self, scheda: dict, url: str):
        """Riporta una scheda su una URL, via websocket CDP."""
        try:
            import websocket

            ws = websocket.create_connection(
                scheda["webSocketDebuggerUrl"], timeout=10,
                suppress_origin=True)
            ws.send(json.dumps({"id": 1, "method": "Page.enable"}))
            ws.send(json.dumps({"id": 2, "method": "Page.navigate",
                                "params": {"url": url}}))
            for _ in range(20):
                r = json.loads(ws.recv())
                if r.get("id") == 2:
                    break
            ws.close()
            self.riportati += 1
            return True
        except Exception:
            return False

    def applica(self) -> dict:
        """Un giro della regola. Restituisce cosa e' stato fatto."""
        schede = self.schede()
        if not schede:
            return {"schede": 0, "chiusi": 0, "riportati": 0,
                    "nota": "browser non raggiungibile o nessuna pagina"}

        dell_app = [s for s in schede if APP_HOST in (s.get("url") or "")]
        altre = [s for s in schede if s not in dell_app]

        # 1. Le schede che non sono l'app: chiuse. Nessuna eccezione, tranne
        #    gli host del percorso di accesso (il login con Google ci passa).
        for s in altre:
            url = s.get("url") or ""
            host_ok = any(h in url for h in HOST_ACCESSO)
            if host_ok:
                continue
            if self._chiudi(s):
                self._log(f"chiusa scheda estranea: {url[:60]}")

        # 2. La pagina dell'app non deve stare su una URL vietata (chat e
        #    simili): la riporto alla radice.
        for s in dell_app:
            url = s.get("url") or ""
            if any(v in url for v in PERCORSI_VIETATI):
                if self._riporta(s, URL_APP):
                    self._log(f"riportata alla radice da: {url[:60]}")

        # 3. Se ci sono piu' schede dell'app, ne tengo una sola.
        if len(dell_app) > 1:
            for s in dell_app[1:]:
                if self._chiudi(s):
                    self._log("chiusa scheda dell'app in piu'")

        return {"schede": len(schede), "chiusi": self.chiusi,
                "riportati": self.riportati}

    def vigila(self, secondi: int = 900, pausa: int = 3):
        """Applica la regola finche' dura l'attesa."""
        scadenza = time.time() + secondi
        while time.time() < scadenza:
            self.applica()
            time.sleep(pausa)
        return {"chiusi": self.chiusi, "riportati": self.riportati}


def main() -> int:
    ap = argparse.ArgumentParser(description="Regola di ferro sulle schede.")
    ap.add_argument("--cdp", type=int, default=9222)
    ap.add_argument("--controlla", action="store_true",
                    help="un solo giro, poi esce")
    ap.add_argument("--una-passo", dest="passo", action="store_true",
                    help="alias di --controlla")
    ap.add_argument("--secondi", type=int, default=900)
    ap.add_argument("--pausa", type=int, default=3)
    args = ap.parse_args()

    g = Guardia(cdp=args.cdp)
    if args.controlla or args.passo:
        print(json.dumps(g.applica(), ensure_ascii=False, indent=1))
        return 0
    print(f"guardia attiva su CDP {args.cdp} per {args.secondi}s")
    print(json.dumps(g.vigila(args.secondi, args.pausa),
                     ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
