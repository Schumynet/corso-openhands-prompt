#!/usr/bin/env bash
# Collauda il cuore: JSON, vincoli Canvas, server MCP, API vera.
set -u

QUI="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
if [ ! -d "$JAVA_HOME" ]; then
    echo "Manca il JDK 17 (cercato in $JAVA_HOME)." >&2
    exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

cd "$QUI" || exit 1
rm -rf out
mkdir -p out
javac -d out app/src/main/java/dev/schumynet/pavo/core/*.java || exit 1
javac -cp out -d out Prova.java || exit 1
java -cp out Prova
