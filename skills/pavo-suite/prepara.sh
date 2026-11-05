#!/usr/bin/env bash
# prepara.sh — mette in piedi tutto quello che serve, su un account nuovo.
#
# Si lancia una volta, dopo aver incollato la skill in un profilo OpenHands
# nuovo. Non da' per scontato niente: controlla, installa, e alla fine dice
# cosa fare.
#
# Uso:
#   bash prepara.sh              # prepara e basta
#   bash prepara.sh --avvia      # prepara e apre subito il browser
#
# Cosa prepara:
#   Xvfb, x11vnc, websockify  -> il display virtuale e il gateway web
#   chromium                  -> il browser
#   noVNC                     -> la pagina web per vederlo (si scarica da sola)
#   websocket-client          -> per parlare col browser (CDP)
#
# Non tocca niente di Pavo e non chiede credenziali.

set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/percorsi.sh"

LOG_DIR="$HERE/logs"
mkdir -p "$LOG_DIR"

AVVIA=0
[ "${1:-}" = "--avvia" ] && AVVIA=1

echo "=============================================================="
echo "  Preparazione ambiente Pavo"
echo "=============================================================="
echo

# --- 1. Permessi -------------------------------------------------------------
SU=""
if [ "$(id -u)" -ne 0 ]; then
    if sudo -n true 2>/dev/null; then
        SU="sudo -n"
    else
        echo "  ERRORE: servono i permessi di amministratore per installare."
        echo "  Questo ambiente non li concede senza password."
        exit 1
    fi
fi

# --- 2. Pacchetti di sistema -------------------------------------------------
echo "[1/4] pacchetti di sistema"
mancanti=()
command -v Xvfb     >/dev/null || mancanti+=(xvfb)
command -v x11vnc   >/dev/null || mancanti+=(x11vnc)
command -v chromium >/dev/null || mancanti+=(chromium)

if [ ${#mancanti[@]} -gt 0 ]; then
    echo "  installo: ${mancanti[*]}"
    $SU apt-get update -qq >"$LOG_DIR/prepara_apt.log" 2>&1
    $SU apt-get install -y --no-install-recommends "${mancanti[@]}" \
        >>"$LOG_DIR/prepara_apt.log" 2>&1
fi
for b in Xvfb x11vnc chromium; do
    printf "  %-10s " "$b"
    command -v "$b" >/dev/null && echo "ok" || { echo "MANCA"; echo "  vedi $LOG_DIR/prepara_apt.log"; exit 1; }
done

# --- 3. Pacchetti Python -----------------------------------------------------
echo "[2/4] pacchetti Python"
python3 -c "import websockify" 2>/dev/null || pip install --quiet websockify \
    >"$LOG_DIR/prepara_pip.log" 2>&1
python3 -c "import websocket"  2>/dev/null || pip install --quiet websocket-client \
    >>"$LOG_DIR/prepara_pip.log" 2>&1
for m in websockify websocket; do
    printf "  %-14s " "$m"
    python3 -c "import $m" 2>/dev/null && echo "ok" || { echo "MANCA"; exit 1; }
done

# --- 4. noVNC ----------------------------------------------------------------
echo "[3/4] noVNC (la pagina per vedere il browser)"
NOVNC="$HERE/novnc"
if [ -f "$NOVNC/vnc.html" ]; then
    echo "  gia' presente"
else
    URL_NOVNC="https://github.com/novnc/noVNC/archive/refs/tags/v1.5.0.zip"
    echo "  scarico da $URL_NOVNC"
    tmp="$(mktemp -d)"
    if curl -sL --max-time 180 -o "$tmp/n.zip" "$URL_NOVNC" \
            && python3 -c "
import sys, zipfile, shutil
from pathlib import Path
z = zipfile.ZipFile('$tmp/n.zip')
radice = z.namelist()[0].split('/')[0]
z.extractall('$tmp')
src = Path('$tmp') / radice
dst = Path('$NOVNC')
dst.mkdir(parents=True, exist_ok=True)
for voce in src.iterdir():
    destinazione = dst / voce.name
    if voce.is_dir(): shutil.copytree(voce, destinazione, dirs_exist_ok=True)
    else: shutil.copy2(voce, destinazione)
"; then
        echo "  scaricato"
    else
        echo "  ERRORE: scaricamento fallito. Senza noVNC non si vede il browser."
        echo "  Serve la rete verso github.com."
        rm -rf "$tmp"; exit 1
    fi
    rm -rf "$tmp"
fi
[ -f "$NOVNC/vnc.html" ] && echo "  vnc.html presente" || { echo "  ERRORE: manca vnc.html"; exit 1; }

# --- 5. Indirizzo pubblico ---------------------------------------------------
echo "[4/4] indirizzo pubblico"
HOST_JSON="$(HERE="$HERE" python3 -c "
import os, sys, json; sys.path.insert(0, os.environ['HERE'])
try:
    from runtime import verifica; print(json.dumps(verifica()))
except Exception as e: print(json.dumps({'errore': str(e)}))
" 2>/dev/null)"
echo "$HOST_JSON" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    if 'errore' in d: print('  non ricavato:', d['errore']); raise SystemExit
    print(f\"  sandbox : {d['sandbox']}\")
    print(f\"  porta 12000 -> {d['url_12000']}\")
    print(f\"  porta 12001 -> {d['url_12001']}\")
except Exception as e:
    print('  non ricavato:', e)
"

echo
echo "=============================================================="
echo "  PRONTO."
echo
echo "  Ora apri la sessione:"
echo "    bash $HERE/avvia_sessione.sh"
echo
echo "  Lo script ti stampa il link da aprire nel tuo browser."
echo "  La' fai il login a Pavo. La password la digiti solo tu."
echo "=============================================================="

if [ "$AVVIA" = "1" ]; then
    echo
    exec bash "$HERE/avvia_sessione.sh"
fi
