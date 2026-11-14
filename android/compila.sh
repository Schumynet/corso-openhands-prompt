#!/usr/bin/env bash
# Compila l'APK. Trova da se' JDK, SDK e Gradle.
set -u

QUI="$(cd "$(dirname "$0")" && pwd)"

trova() {
    # $1 = variabile d'ambiente, $2 = percorsi da provare
    local attuale="${!1:-}"
    if [ -n "$attuale" ] && [ -d "$attuale" ]; then echo "$attuale"; return; fi
    for p in $2; do
        if [ -d "$p" ]; then echo "$p"; return; fi
    done
    echo ""
}

JAVA_HOME="$(trova JAVA_HOME "$HOME/tools/jdk17 /usr/lib/jvm/java-17-openjdk-amd64")"
if [ -z "$JAVA_HOME" ]; then
    echo "Manca il JDK 17. Installalo, oppure mettilo in ~/tools/jdk17." >&2
    exit 1
fi

ANDROID_HOME="$(trova ANDROID_HOME "$HOME/tools/android-sdk $HOME/Android/Sdk")"
if [ -z "$ANDROID_HOME" ]; then
    echo "Manca l'SDK Android. Installalo, oppure mettilo in ~/tools/android-sdk." >&2
    exit 1
fi

GRADLE="$(command -v gradle || true)"
if [ -z "$GRADLE" ]; then
    for p in "$HOME/tools/gradle-8.7/bin/gradle" "$HOME/gradle/bin/gradle"; do
        if [ -x "$p" ]; then GRADLE="$p"; break; fi
    done
fi
if [ -z "$GRADLE" ]; then
    echo "Manca Gradle. Installalo, oppure mettilo in ~/tools/gradle-8.7." >&2
    exit 1
fi

export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "JDK:    $JAVA_HOME"
echo "SDK:    $ANDROID_HOME"
echo "Gradle: $GRADLE"
echo

cd "$QUI" || exit 1
"$GRADLE" assembleRelease --no-daemon || exit 1

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/pavo-mcp-1.0.0.apk
cd dist || exit 1
sha256sum pavo-mcp-1.0.0.apk > pavo-mcp-1.0.0.apk.sha256

echo
echo "APK pronto: $QUI/dist/pavo-mcp-1.0.0.apk"
du -h pavo-mcp-1.0.0.apk
