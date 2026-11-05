#!/usr/bin/env python3
"""Login su Pavo AI dal browser del tuo computer.

Apre il tuo Chrome (o Chromium, Edge, Brave) su app.pavo-ai.work, aspetta che
tu faccia il login, poi salva cookie e token in `pavo_login/pavo_state.json`.
Da li' in poi Claude usa quel file: non gli serve la password, e non la vede mai.

Uso:
    python3 login.py                  # apre il browser e aspetta
    python3 login.py --stato          # dice solo se il token e' valido
    python3 login.py --porta 9333     # se la 9222 e' occupata
    python3 login.py --browser /percorso/chrome

Perche' non usa Playwright: serve solo aprire il browser e leggere due cose dal
suo protocollo di debug. Si fa con la libreria standard, senza installare nulla.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

HERE = Path(__file__).resolve().parent
PROFILO = HERE / "pavo_login" / "pavo_profile"
STATO = HERE / "pavo_login" / "pavo_state.json"
APP_HOST = "app.pavo-ai.work"
ORIGINE = "https://app.pavo-ai.work"
URL_LOGIN = "https://app.pavo-ai.work/"
ATTESA = 15 * 60

BINARI = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/usr/bin/microsoft-edge",
    "/snap/bin/chromium",
]


def trova_browser(esplicito: str = "") -> str:
    """Il primo browser utilizzabile. `esplicito` vince su tutto."""
    if esplicito:
        if not Path(esplicito).is_file():
            raise SystemExit(f"ERRORE: browser non trovato in {esplicito}")
        return esplicito
    for p in BINARI:
        if Path(p).is_file():
            return p
    for nome in ("google-chrome", "chromium", "chromium-browser", "chrome",
                 "msedge", "brave-browser"):
        trovato = shutil.which(nome)
        if trovato:
            return trovato
    raise SystemExit(
        "ERRORE: non trovo un browser.\n"
        "Passalo a mano:  python3 login.py --browser /percorso/del/browser"
    )


class WS:
    """Client WebSocket minimo, solo per parlare col protocollo di debug.

    Serve una sessantina di righe: il protocollo di Chrome non ha un modo HTTP
    per leggere il localStorage, quindi o si usa una libreria o si fa cosi'.
    Cosi' non c'e' niente da installare.
    """

    def __init__(self, url: str, timeout: int = 20):
        u = urlparse(url)
        self.sock = socket.create_connection((u.hostname, u.port or 80), timeout)
        chiave = base64.b64encode(os.urandom(16)).decode()
        richiesta = (
            f"GET {u.path or '/'} HTTP/1.1\r\n"
            f"Host: {u.hostname}:{u.port}\r\n"
            "Upgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {chiave}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        self.sock.sendall(richiesta.encode())
        testa = b""
        while b"\r\n\r\n" not in testa:
            pezzo = self.sock.recv(4096)
            if not pezzo:
                raise RuntimeError("connessione chiusa durante l'handshake")
            testa += pezzo
        if b"101" not in testa.split(b"\r\n")[0]:
            raise RuntimeError(f"handshake rifiutato: {testa.split(b'\\r\\n')[0]!r}")
        self._buf = testa.split(b"\r\n\r\n", 1)[1]

    def invia(self, testo: str) -> None:
        dati = testo.encode()
        n = len(dati)
        testata = bytearray([0x81])          # fin + opcode testo
        if n < 126:
            testata.append(0x80 | n)
        elif n < 65536:
            testata.append(0x80 | 126)
            testata += struct.pack(">H", n)
        else:
            testata.append(0x80 | 127)
            testata += struct.pack(">Q", n)
        maschera = os.urandom(4)
        testata += maschera
        mascherato = bytes(b ^ maschera[i % 4] for i, b in enumerate(dati))
        self.sock.sendall(bytes(testata) + mascherato)

    def _leggi(self, quanti: int) -> bytes:
        while len(self._buf) < quanti:
            pezzo = self.sock.recv(65536)
            if not pezzo:
                raise RuntimeError("connessione chiusa")
            self._buf += pezzo
        dato, self._buf = self._buf[:quanti], self._buf[quanti:]
        return dato

    def ricevi(self) -> str:
        """Un messaggio completo, ricomponendo i pezzi se e' spezzettato."""
        pezzi: list[bytes] = []
        while True:
            b0, b1 = self._leggi(2)
            fin, opcode = b0 & 0x80, b0 & 0x0F
            lungo = b1 & 0x7F
            if lungo == 126:
                lungo = struct.unpack(">H", self._leggi(2))[0]
            elif lungo == 127:
                lungo = struct.unpack(">Q", self._leggi(8))[0]
            if b1 & 0x80:
                maschera = self._leggi(4)
                corpo = bytes(c ^ maschera[i % 4]
                              for i, c in enumerate(self._leggi(lungo)))
            else:
                corpo = self._leggi(lungo)
            if opcode == 0x8:
                raise RuntimeError("il browser ha chiuso la connessione")
            if opcode == 0x9:                 # ping -> pong
                self.sock.sendall(b"\x8a\x80" + os.urandom(4))
                continue
            if opcode == 0xA:
                continue
            pezzi.append(corpo)
            if fin:
                return b"".join(pezzi).decode("utf-8", "ignore")

    def chiudi(self) -> None:
        try:
            self.sock.close()
        except Exception:
            pass


class CDP:
    """Ponte verso una scheda, col protocollo di debug di Chrome."""

    def __init__(self, ws_url: str, timeout: int = 20):
        self.ws = WS(ws_url, timeout)
        self._n = 0
        self._invia("Runtime.enable")
        self._invia("Network.enable")

    def _invia(self, metodo: str, parametri: dict | None = None) -> dict:
        self._n += 1
        self.ws.invia(json.dumps({"id": self._n, "method": metodo,
                                  "params": parametri or {}}))
        while True:
            risposta = json.loads(self.ws.ricevi())
            if risposta.get("id") == self._n:
                return risposta

    def valuta(self, espressione: str):
        r = self._invia("Runtime.evaluate",
                        {"expression": espressione, "returnByValue": True})
        return (r.get("result", {}).get("result", {}) or {}).get("value")

    def cookie(self) -> list[dict]:
        return self._invia("Network.getAllCookies").get("result", {}).get("cookies", [])

    def chiudi(self) -> None:
        self.ws.chiudi()


def _json_da(url: str, timeout: int = 5):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "ignore"))


def scheda_pavo(porta: int) -> dict | None:
    """La scheda di Pavo, fra quelle aperte."""
    try:
        for s in _json_da(f"http://127.0.0.1:{porta}/json"):
            if s.get("type") == "page" and APP_HOST in (s.get("url") or ""):
                return s
    except Exception:
        return None
    return None


def porta_libera(porta: int) -> bool:
    with socket.socket() as s:
        s.settimeout(1)
        return s.connect_ex(("127.0.0.1", porta)) != 0


def apri(browser: str, porta: int) -> subprocess.Popen:
    """Apre il browser col debug attivo, su un profilo dedicato.

    Il profilo e' separato da quello di tutti i giorni: cosi' non si tocca la
    tua navigazione, e il login a Pavo resta salvato tra un avvio e l'altro.
    """
    PROFILO.mkdir(parents=True, exist_ok=True)
    argomenti = [
        browser,
        f"--remote-debugging-port={porta}",
        f"--user-data-dir={PROFILO}",
        "--no-first-run",
        "--no-default-browser-check",
        URL_LOGIN,
    ]
    return subprocess.Popen(argomenti, stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL)


def leggi_sessione(cmd: CDP) -> dict | None:
    """Token e cookie. None finche' il login non e' stato fatto."""
    token = cmd.valuta("window.localStorage.getItem('token')")
    if not token:
        return None
    grezzo = cmd.valuta(
        "JSON.stringify(Object.fromEntries("
        "Object.keys(window.localStorage).map("
        "k => [k, window.localStorage.getItem(k)])))")
    try:
        voci = json.loads(grezzo) if grezzo else {}
    except json.JSONDecodeError:
        voci = {}
    return {
        "cookies": cmd.cookie(),
        "origins": [{
            "origin": ORIGINE,
            "localStorage": [{"name": k, "value": v} for k, v in voci.items()],
        }],
    }


def scadenza(token: str) -> str:
    """La scadenza del token, per dire all'utente quando scadra'."""
    try:
        p = token.split(".")[1]
        p += "=" * (-len(p) % 4)
        exp = json.loads(base64.urlsafe_b64decode(p)).get("exp")
        if exp:
            return time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime(exp))
    except Exception:
        pass
    return "sconosciuta"


def stato_attuale() -> dict:
    """Legge il file di sessione e dice se il token e' ancora buono."""
    if not STATO.is_file():
        return {"ok": False, "motivo": "nessuna sessione salvata",
                "rimedio": f"python3 {HERE / 'login.py'}"}
    try:
        d = json.loads(STATO.read_text(encoding="utf-8"))
    except Exception as e:
        return {"ok": False, "motivo": f"file illeggibile: {e}"}
    token = ""
    for o in d.get("origins", []):
        for v in o.get("localStorage", []):
            if v.get("name") == "token":
                token = v.get("value", "")
    if not token:
        return {"ok": False, "motivo": "sessione senza token",
                "rimedio": f"python3 {HERE / 'login.py'}"}
    exp = scadenza(token)
    scaduto = "sconosciuta" not in exp and exp < time.strftime("%Y-%m-%d %H:%M UTC",
                                                               time.gmtime())
    return {"ok": not scaduto, "scadenza": exp,
            "motivo": "token scaduto" if scaduto else "token valido",
            "cookie": len(d.get("cookies", []))}


def attendi_login(porta: int, secondi: int) -> dict | None:
    """Aspetta che il token compaia nel localStorage. None se scade il tempo."""
    fine = time.time() + secondi
    ultimo_avviso = 0.0
    while time.time() < fine:
        scheda = scheda_pavo(porta)
        if scheda:
            try:
                cmd = CDP(scheda["webSocketDebuggerUrl"])
            except Exception:
                time.sleep(2)
                continue
            try:
                sessione = leggi_sessione(cmd)
            finally:
                cmd.chiudi()
            if sessione:
                return sessione
        if time.time() - ultimo_avviso > 30:
            ultimo_avviso = time.time()
            print("  ...aspetto il login (Ctrl+C per annullare)", flush=True)
        time.sleep(3)
    return None


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Login su Pavo AI dal tuo browser, e salvataggio della sessione.")
    ap.add_argument("--porta", type=int, default=9222,
                    help="porta di debug del browser (default 9222)")
    ap.add_argument("--browser", default="", help="percorso del browser da usare")
    ap.add_argument("--attesa", type=int, default=ATTESA,
                    help="secondi da aspettare per il login (default 900)")
    ap.add_argument("--stato", action="store_true",
                    help="dice solo se la sessione salvata e' valida")
    ap.add_argument("--niente-browser", action="store_true",
                    help="non aprire il browser: usa quello gia' in ascolto")
    a = ap.parse_args()

    if a.stato:
        print(json.dumps(stato_attuale(), ensure_ascii=False, indent=2))
        return 0 if stato_attuale()["ok"] else 1

    print("=" * 62)
    print("  LOGIN PAVO AI")
    print("=" * 62)

    browser = None
    if not a.niente_browser:
        browser = trova_browser(a.browser)
        if not porta_libera(a.porta):
            print(f"  la porta {a.porta} e' occupata: uso un browser gia' aperto")
            browser = None

    if browser:
        print(f"  apro: {browser}")
        print(f"  profilo: {PROFILO}")
        apri(browser, a.porta)
    else:
        print(f"  mi collego al browser gia' attivo sulla porta {a.porta}")

    print()
    print("  Nel browser che si e' aperto, fai il login a Pavo (Google o email).")
    print("  La password la digiti solo tu: questo script non la vede.")
    print("  Quando sei dentro, la sessione si salva da sola.")
    print()

    sessione = attendi_login(a.porta, a.attesa)
    if not sessione:
        print("\n  TEMPO SCADUTO: nessun login rilevato.")
        print(f"  Riprova:  python3 {HERE / 'login.py'}")
        return 1

    STATO.parent.mkdir(parents=True, exist_ok=True)
    STATO.write_text(json.dumps(sessione, ensure_ascii=False), encoding="utf-8")
    try:
        STATO.chmod(0o600)
    except OSError:
        pass

    token = ""
    for o in sessione["origins"]:
        for v in o["localStorage"]:
            if v["name"] == "token":
                token = v["value"]

    print("\n" + "=" * 62)
    print("  LOGIN FATTO")
    print("=" * 62)
    print(f"  sessione : {STATO}")
    print(f"  cookie   : {len(sessione['cookies'])}")
    print(f"  scadenza : {scadenza(token)}")
    print()
    print("  Il file contiene token e cookie: e' una credenziale.")
    print("  Non condividerlo e non metterlo su Git.")
    print()
    print("  Ora Claude puo' usare Pavo. Per controllare:")
    print(f"    python3 {HERE / 'login.py'} --stato")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n  annullato.")
        raise SystemExit(130)
