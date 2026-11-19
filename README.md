# ObraSnap

Cámara de obra con stamp de PK, GPS, fecha y actividad. Funciona como página web (GitHub Pages) y como app de Android independiente.

- Web: https://2351102227luis-source.github.io/obrasnap/
- APK: https://2351102227luis-source.github.io/obrasnap/descargas/ObraSnap.apk

## Dónde está cada cosa

| Archivo | Qué es |
|---|---|
| `index.html` | **Toda la app** (pantallas, cámara, stamp, PK, galería, PDF). Es lo que se edita casi siempre. |
| `EJE.kmz` | Eje del proyecto que se carga solo. Para cambiar de proyecto, reemplázalo. |
| `fonts/` | Tipografías incluidas (funcionan sin internet). |
| `sw.js`, `manifest.json` | Solo para la versión web (modo sin señal e instalación desde Chrome). |
| `version.json` | Versión de la app. La app instalada lo consulta para avisar que hay actualización. |
| `android/` | Proyecto Android: `MainActivity.java` (permisos, guardar en Galería, compartir, botón atrás). |
| `descargas/ObraSnap.apk` | El APK publicado. |

## Cómo cambiar algo y publicarlo

1. Edita `index.html` (o lo que haga falta) y prueba en el navegador.
2. **Solo web:** sube los cambios a `main` y GitHub Pages los publica en minutos.
3. **App Android:** la app lleva los archivos dentro, así que hay que compilar un APK nuevo:
   1. Sube el número en `version.json` (`versionCode` +1, `versionName` y `notas`).
   2. Ejecuta `android/build-apk.sh` (necesita el Android SDK y la llave de firma).
   3. Sube `descargas/ObraSnap.apk` y `version.json`. Las apps instaladas mostrarán
      "Nueva versión disponible" en Config → App.

⚠️ La llave de firma (`obrasnap-release.jks`) **no** está en el repo. Sin ella no se pueden publicar
actualizaciones que se instalen encima de la app actual.

## Diagnóstico

Config → Diagnóstico muestra versión, cámara, GPS, eje y último error.
**Ver registro / Compartir registro** guarda los últimos 400 eventos y errores para enviarlos a soporte.
En la app Android también se puede inspeccionar desde una PC con `chrome://inspect` (USB, modo desarrollador).
