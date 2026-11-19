#!/usr/bin/env bash
# Compila el APK firmado de ObraSnap y lo deja en descargas/ObraSnap.apk
# Requisitos: JDK 17+, Gradle 8.9+, Android SDK (ANDROID_HOME) y android/keystore.properties con la llave.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Define ANDROID_HOME con la ruta del Android SDK}"
[ -f keystore.properties ] || { echo "Falta android/keystore.properties (ver LEEME.txt de la llave)"; exit 1; }
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleRelease --no-daemon -q
mkdir -p ../descargas
cp app/build/outputs/apk/release/app-release.apk ../descargas/ObraSnap.apk
echo "Listo: descargas/ObraSnap.apk ($(python3 -c "import json;v=json.load(open('../version.json'));print(v['versionName'],'código',v['versionCode'])"))"
