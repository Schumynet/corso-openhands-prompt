#!/usr/bin/env bash
# percorsi.sh — trova le cartelle di lavoro, senza contare i livelli.
#
# Va incluso con:  . "$(dirname "${BASH_SOURCE[0]}")/percorsi.sh"
#
# Perche' non si contano i livelli
# ---------------------------------
# Questa skill puo' stare in due posti diversi:
#
#   dentro il progetto      <progetto>/.agents/skills/pavo-suite/
#   incollata da sola       <cartella>/.agents/skills/pavo-suite/
#
# Contare i livelli funzionerebbe in un caso e non nell'altro. Quindi si cerca.
#
# Perche' non si cerca `runtime.py`
# ---------------------------------
# La skill contiene una **copia** di `runtime.py`: cercandolo si troverebbe la
# skill stessa, e la cartella calcolata sarebbe sbagliata. Si cerca invece
# `pavo_login`, che e' unica.

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# La cartella che contiene `.agents`: e' la radice del progetto.
SENNA=""
d="$HERE"
while [ "$d" != "/" ]; do
    if [ -d "$d/.agents" ]; then SENNA="$d"; break; fi
    d="$(dirname "$d")"
done
if [ -z "$SENNA" ]; then
    echo "ERRORE: non trovo la cartella .agents risalendo da $HERE" >&2
    echo "La skill deve stare dentro <progetto>/.agents/skills/." >&2
    exit 1
fi

# La cartella di lavoro Pavo: dove sta `pavo_login`.
#   1. se `PAVO_LOGIN` e' impostata, vince;
#   2. altrimenti si cerca `pavo_login` risalendo (caso del progetto esistente);
#   3. altrimenti si crea accanto alla cartella `.agents` (caso del profilo nuovo).
if [ -n "${PAVO_LOGIN:-}" ]; then
    :
else
    PAVO_LOGIN=""
    d="$HERE"
    while [ "$d" != "/" ]; do
        if [ -d "$d/pavo_login" ]; then PAVO_LOGIN="$d/pavo_login"; break; fi
        d="$(dirname "$d")"
    done
    [ -n "$PAVO_LOGIN" ] || PAVO_LOGIN="$SENNA/pavo_login"
fi
export PAVO_LOGIN

# `PROGETTO` e' la cartella che contiene `pavo_login`. Serve a chi cerca i file
# del progetto (per esempio la sessione).
PROGETTO="$(dirname "$PAVO_LOGIN")"
export PROGETTO SENNA

mkdir -p "$PAVO_LOGIN"
