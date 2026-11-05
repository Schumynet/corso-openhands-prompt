#!/usr/bin/env bash
# ferma_sessione.sh — chiude tutto quello che ha aperto avvia_sessione.sh.
#
# Uso:
#   bash ferma_sessione.sh
#   PORT=12001 DISPLAY_NUM=99 VNC_PORT=5901 bash ferma_sessione.sh
#   ACCOUNT=secondo CDP_PORT=9223 DISPLAY_NUM=98 VNC_PORT=5901 \
#       PORT=12001 bash ferma_sessione.sh

set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROGETTO="$(cd "$HERE/../../../.." && pwd)"
PAVO_LOGIN="$PROGETTO/pavo_login"

PORT="${PORT:-12000}"
DISPLAY_NUM="${DISPLAY_NUM:-99}"
VNC_PORT="${VNC_PORT:-5900}"
CDP_PORT="${CDP_PORT:-9222}"

ACCOUNT="${ACCOUNT:-}"
SUFFISSO=""
[ -n "$ACCOUNT" ] && SUFFISSO="_${ACCOUNT}"

echo "chiudo la sessione Pavo..."

# La guardia si ferma per prima: altrimenti ricrea da sola i controlli.
PID_GUARDIA="$PAVO_LOGIN/logs/guardia${SUFFISSO}.pid"
if [ -f "$PID_GUARDIA" ]; then
    kill "$(cat "$PID_GUARDIA")" 2>/dev/null && echo "  guardia fermata"
    rm -f "$PID_GUARDIA"
fi

# Ogni processo viene individuato da una stringa che lo identifica con
# precisione: cosi' non si rischia di chiudere altri processi simili.
pkill -f "guardia.py --cdp ${CDP_PORT}" 2>/dev/null
pkill -f "remote-debugging-port=${CDP_PORT}" 2>/dev/null && echo "  browser chiuso"
pkill -f "websockify.*${PORT}"                2>/dev/null && echo "  gateway web chiuso"
pkill -f "x11vnc .*:${VNC_PORT}"              2>/dev/null && echo "  server VNC chiuso"
pkill -f "Xvfb :${DISPLAY_NUM}"               2>/dev/null && echo "  display virtuale chiuso"

echo "fatto. La sessione salvata resta in pavo_state${SUFFISSO}.json."
