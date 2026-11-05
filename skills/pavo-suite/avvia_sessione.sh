#!/usr/bin/env bash
# avvia_sessione.sh — prepara tutto e apre Pavo AI in un browser navigabile.
#
# Cosa fa, in ordine:
#   1. installa i pezzi mancanti (Xvfb, x11vnc, websockify, chromium)
#   2. avvia un display virtuale, un server VNC e un gateway web (noVNC)
#   3. apre Chromium su app.pavo-ai.work dentro quel display
#   4. stampa il link da aprire nel TUO browser
#   5. aspetta che tu faccia il login e salva la sessione in pavo_state.json
#
# Uso:
#   bash avvia_sessione.sh              # porta 12000, account predefinito
#   PORT=12001 bash avvia_sessione.sh   # porta diversa
#   SOLO_AVVIO=1 bash avvia_sessione.sh # avvia senza aspettare il login
#
# Un altro account (profilo e sessione separati):
#   ACCOUNT=secondo PORT=12001 DISPLAY_NUM=98 VNC_PORT=5901 CDP_PORT=9223 \
#       bash avvia_sessione.sh
#
# Il login lo fai TU: la password non passa mai da qui.

set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/percorsi.sh"

PORT="${PORT:-12000}"
DISPLAY_NUM="${DISPLAY_NUM:-99}"
VNC_PORT="${VNC_PORT:-5900}"
SCREEN="${SCREEN:-1600x1000x24}"
CDP_PORT="${CDP_PORT:-9222}"

# La pagina noVNC: prima quella della skill (autosufficiente), poi quella del
# progetto. La prepara `prepara.sh`.
NOVNC="$HERE/novnc"
[ -f "$NOVNC/vnc.html" ] || NOVNC="$PAVO_LOGIN/novnc"
if [ ! -f "$NOVNC/vnc.html" ]; then
    echo "ERRORE: manca noVNC. Esegui prima:  bash $HERE/prepara.sh"
    exit 1
fi

# L'indirizzo pubblico lo ricava `runtime.py`: il nome del sandbox cambia con
# l'account, quindi non si puo' scrivere qui. `PAVO_RUNTIME_HOST` lo forza.
# `url_base` sceglie anche work-1 o work-2 in base alla porta.
URL_BASE="$(python3 -c "
import sys; sys.path.insert(0, '$HERE')
from runtime import url_base; print(url_base($PORT))
" 2>/dev/null)"
[ -n "$URL_BASE" ] || URL_BASE="https://work-1-dsczaeegyhjtbats.prod-runtime.all-hands.dev"

# Il nome dell'account separa profilo e file di sessione. Cosi' due account
# possono convivere senza sovrascriversi: il predefinito tiene i nomi storici,
# gli altri prendono un suffisso.
ACCOUNT="${ACCOUNT:-}"
if [ -n "$ACCOUNT" ]; then
    SUFFISSO="_${ACCOUNT}"
else
    SUFFISSO=""
fi
PROFILO="$PAVO_LOGIN/pavo_profile${SUFFISSO}"
STATO="$PAVO_LOGIN/pavo_state${SUFFISSO}.json"

LOG_DIR="$PAVO_LOGIN/logs"
mkdir -p "$LOG_DIR"
export DISPLAY=":${DISPLAY_NUM}"

echo "=============================================================="
echo "  Vento in Curva — sessione Pavo AI"
[ -n "$ACCOUNT" ] && echo "  account: $ACCOUNT"
echo "=============================================================="
echo

# --- 1. Pezzi mancanti -------------------------------------------------------
manca=()
command -v Xvfb      >/dev/null || manca+=("xvfb")
command -v x11vnc    >/dev/null || manca+=("x11vnc")
command -v chromium  >/dev/null || manca+=("chromium")
python3 -c "import websockify" 2>/dev/null || manca+=("websockify")
python3 -c "import websocket"  2>/dev/null || manca+=("websocket-client")

if [ ${#manca[@]} -gt 0 ]; then
    echo "[1/5] installo: ${manca[*]}"
    apt_su=()
    [ "$(id -u)" -ne 0 ] && apt_su=(sudo -n)
    apt_pkg=()
    pip_pkg=()
    for p in "${manca[@]}"; do
        case "$p" in
            websockify|websocket-client) pip_pkg+=("$p") ;;
            *) apt_pkg+=("$p") ;;
        esac
    done
    if [ ${#apt_pkg[@]} -gt 0 ]; then
        "${apt_su[@]}" apt-get install -y --no-install-recommends \
            "${apt_pkg[@]}" >"$LOG_DIR/installazione.log" 2>&1 \
            || echo "  avviso: installazione apt fallita, vedi $LOG_DIR/installazione.log"
    fi
    if [ ${#pip_pkg[@]} -gt 0 ]; then
        pip install --quiet "${pip_pkg[@]}" >"$LOG_DIR/installazione.log" 2>&1 \
            || echo "  avviso: installazione pip fallita"
    fi
else
    echo "[1/5] tutto installato"
fi

# --- 2. Stack grafico --------------------------------------------------------
echo "[2/5] avvio display virtuale, VNC e gateway web"

pkill -f "Xvfb :${DISPLAY_NUM}" 2>/dev/null
pkill -f "x11vnc .*:${VNC_PORT}" 2>/dev/null
pkill -f "websockify.*${PORT}" 2>/dev/null
pkill -f "remote-debugging-port=${CDP_PORT}" 2>/dev/null
sleep 1

Xvfb ":${DISPLAY_NUM}" -screen 0 "$SCREEN" >"$LOG_DIR/xvfb.log" 2>&1 &
sleep 2

x11vnc -display ":${DISPLAY_NUM}" -rfbport "${VNC_PORT}" -forever -shared -nopw \
    -quiet >"$LOG_DIR/x11vnc.log" 2>&1 &
sleep 2

python3 -m websockify --web "$NOVNC" "${PORT}" "localhost:${VNC_PORT}" \
    >"$LOG_DIR/websockify.log" 2>&1 &
sleep 3

if curl -s -o /dev/null --max-time 5 "http://localhost:${PORT}/vnc.html"; then
    echo "  gateway web: attivo sulla porta ${PORT}"
else
    echo "  ERRORE: il gateway non risponde. Vedi $LOG_DIR/websockify.log"
    exit 1
fi

# --- 3. Browser --------------------------------------------------------------
echo "[3/5] apro Chromium su app.pavo-ai.work"

mkdir -p "$PROFILO"

chromium --no-sandbox --disable-dev-shm-usage \
    --user-data-dir="$PROFILO" \
    --remote-debugging-port="${CDP_PORT}" \
    --window-size=1600,1000 \
    --no-first-run --no-default-browser-check \
    "https://app.pavo-ai.work/" >"$LOG_DIR/chromium${SUFFISSO}.log" 2>&1 &

# Aspetto che il browser risponda al protocollo di debug.
for _ in $(seq 1 30); do
    if curl -s --max-time 3 "http://localhost:${CDP_PORT}/json/version" \
            >/dev/null 2>&1; then
        echo "  browser pronto"
        break
    fi
    sleep 2
done

# --- 3b. REGOLA DI FERRO -----------------------------------------------------
# Il browser serve a una cosa sola: stare su app.pavo-ai.work per il login.
# Non apre chat, non apre schede, non naviga altrove. La guardia lo impone.
echo "[3b/5] attivo la regola di ferro sulle schede"
python3 "$HERE/guardia.py" --cdp "${CDP_PORT}" --controlla >/dev/null 2>&1
(
    while true; do
        python3 "$HERE/guardia.py" --cdp "${CDP_PORT}" --controlla \
            >/dev/null 2>&1
        sleep 3
    done
) >"$LOG_DIR/guardia${SUFFISSO}.log" 2>&1 &
echo $! > "$LOG_DIR/guardia${SUFFISSO}.pid"
echo "  guardia attiva: una sola pagina, nessuna chat"

# --- 4. Il link --------------------------------------------------------------
echo
echo "=============================================================="
echo "  APRI QUESTO LINK NEL TUO BROWSER:"
echo
echo "    ${URL_BASE}/vnc.html?autoconnect=1&resize=scale"
echo
echo "  Vedrai la pagina di Pavo AI. Fai il login (Google o email)."
echo "  La password la digiti solo tu, dentro quella pagina."
echo "=============================================================="
echo

if [ "${SOLO_AVVIO:-0}" = "1" ]; then
    echo "  (SOLO_AVVIO=1: non aspetto il login)"
    exit 0
fi

# --- 5. Aspetto il login e salvo --------------------------------------------
echo "[4/5] aspetto che tu faccia il login (max 15 minuti)..."
echo

python3 "$HERE/salva_sessione.py" --cdp "${CDP_PORT}" \
    --uscita "$STATO" --attesa 900

esito=$?

if [ $esito -eq 0 ]; then
    echo
    echo "[5/5] sessione salvata in $STATO"
    echo
    echo "  Da qui in avanti:"
    echo "    python3 senna_series/pavo_mcp.py     # avvia l'MCP per Claude"
    echo "    python3 -c 'import pavo_api as A; print(A.saldo(A.Pavo()))'"
    echo
    echo "  Per fermare tutto: bash $HERE/ferma_sessione.sh"
else
    echo
    echo "[5/5] login non completato (codice $esito)."
    echo "  Il browser resta aperto: puoi riprovare con"
    echo "    python3 $HERE/salva_sessione.py --cdp $CDP_PORT --uscita $STATO"
fi
