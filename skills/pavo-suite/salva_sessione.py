#!/usr/bin/env python3
"""salva_sessione.py — aspetta il login su Pavo e salva la sessione.

Come funziona
-------------
Si collega al browser gia' aperto tramite il protocollo di debug di Chrome
(CDP), sul display virtuale. Non apre un browser suo: usa quello che vedi
attraverso noVNC, dove stai facendo il login.

Ogni pochi secondi guarda il `localStorage` della pagina Pavo. Quando compare
il token, legge anche i cookie e scrive tutto in `pavo_state.json`, nello stesso
formato che usano `pavo_api.py` e `pavo_mcp.py`.

La password non passa mai da qui: la digiti tu, nel browser.

Uso:
    python3 salva_sessione.py
    python3 salva_sessione.py --cdp 9222 --uscita ../pavo_login/pavo_state.json
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

APP_HOST = "app.pavo-ai.work"
ORIGINE = "https://app.pavo-ai.work"


def _leggi_json(url: str, timeout: int = 5):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "ignore"))


def trova_pagina(cdp: int) -> dict | None:
    """La scheda di Pavo, fra quelle aperte nel browser."""
    try:
        schede = _leggi_json(f"http://localhost:{cdp}/json")
    except Exception:
        return None
    for s in schede:
        if s.get("type") == "page" and APP_HOST in (s.get("url") or ""):
            return s
    return None


class Comandi:
    """Ponte verso una scheda del browser, con il protocollo CDP."""

    def __init__(self, ws_url: str, timeout: int = 15):
        import websocket

        # `suppress_origin` serve: Chrome rifiuta le connessioni che dichiarano
        # un Origin, e qui non ne serve nessuno.
        self.ws = websocket.create_connection(ws_url, timeout=timeout,
                                              suppress_origin=True)
        self._n = 0
        self.invia("Runtime.enable")
        self.invia("Network.enable")

    def invia(self, metodo: str, parametri: dict | None = None) -> dict:
        self._n += 1
        self.ws.send(json.dumps({"id": self._n, "method": metodo,
                                 "params": parametri or {}}))
        while True:
            risposta = json.loads(self.ws.recv())
            if risposta.get("id") == self._n:
                return risposta

    def valuta(self, espressione: str):
        r = self.invia("Runtime.evaluate",
                       {"expression": espressione, "returnByValue": True})
        return (r.get("result", {}).get("result", {}) or {}).get("value")

    def chiudi(self):
        try:
            self.ws.close()
        except Exception:
            pass


def leggi_sessione(cmd: Comandi) -> dict | None:
    """Token e localStorage. None se il token non c'e' ancora."""
    token = cmd.valuta("window.localStorage.getItem('token')")
    if not token:
        return None

    grezzo = cmd.valuta("JSON.stringify(Object.fromEntries("
                        "Object.keys(window.localStorage).map("
                        "k => [k, window.localStorage.getItem(k)])))")
    try:
        voci = json.loads(grezzo) if grezzo else {}
    except json.JSONDecodeError:
        voci = {}

    r = cmd.invia("Network.getAllCookies")
    cookie = r.get("result", {}).get("cookies", [])

    return {
        "cookies": cookie,
        "origins": [{
            "origin": ORIGINE,
            "localStorage": [{"name": k, "value": v} for k, v in voci.items()],
        }],
    }


def scadenza(token: str) -> str:
    """La scadenza del token, per informare l'utente."""
    import base64

    try:
        p = token.split(".")[1]
        p += "=" * (-len(p) % 4)
        exp = json.loads(base64.urlsafe_b64decode(p)).get("exp")
        if exp:
            return time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime(exp))
    except Exception:
        pass
    return "sconosciuta"


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Aspetta il login su Pavo e salva la sessione.")
    ap.add_argument("--cdp", type=int, default=9222,
                    help="porta di debug del browser (default 9222)")
    ap.add_argument("--uscita", default="../pavo_login/pavo_state.json",
                    help="dove scrivere il file di sessione")
    ap.add_argument("--attesa", type=int, default=900,
                    help="quanti secondi aspettare il login (default 900)")
    ap.add_argument("--intervallo", type=int, default=3,
                    help="ogni quanti secondi controllare (default 3)")
    args = ap.parse_args()

    uscita = Path(args.uscita).expanduser().resolve()
    uscita.parent.mkdir(parents=True, exist_ok=True)

    scadenza_attesa = time.time() + args.attesa
    ultimo_avviso = 0.0

    while time.time() < scadenza_attesa:
        pagina = trova_pagina(args.cdp)
        if not pagina:
            if time.time() - ultimo_avviso > 30:
                print("  ... nessuna scheda Pavo aperta. Apri il link e naviga "
                      "su app.pavo-ai.work")
                ultimo_avviso = time.time()
            time.sleep(args.intervallo)
            continue

        try:
            cmd = Comandi(pagina["webSocketDebuggerUrl"])
        except Exception as e:
            print(f"  ... non riesco a collegarmi al browser ({type(e).__name__})")
            time.sleep(args.intervallo)
            continue

        try:
            sessione = leggi_sessione(cmd)
        except Exception:
            sessione = None
        finally:
            cmd.chiudi()

        if sessione:
            uscita.write_text(
                json.dumps(sessione, ensure_ascii=False, indent=1),
                encoding="utf-8")
            uscita.chmod(0o600)
            token = next(v["value"] for o in sessione["origins"]
                         for v in o["localStorage"] if v["name"] == "token")
            print(f"  login rilevato: {len(sessione['cookies'])} cookie")
            print(f"  token valido fino al {scadenza(token)}")
            return 0

        if time.time() - ultimo_avviso > 30:
            print("  ... in attesa del login")
            ultimo_avviso = time.time()
        time.sleep(args.intervallo)

    print("  tempo scaduto: il login non e' stato completato")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
