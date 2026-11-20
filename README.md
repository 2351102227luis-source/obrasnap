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

## Obras (configuración compartible)

Config → **Obras**: cada obra guarda nombre, actividades frecuentes, formatos (PK, fecha, hora, coordenadas),
estilo y posiciones del stamp, ajustes de cámara, logo y —opcional— el eje con su calibración.

- **Compartir configuración de esta obra** genera un archivo `NombreDeObra.obrasnap` (JSON) para mandar por WhatsApp/correo.
- En la app Android basta con tocar el archivo y elegir **Abrir con ObraSnap**; en la web, **Importar configuración**.
- Si ya existe una obra con el mismo nombre, se reemplaza su configuración (las fotos no se tocan).
- **+ Nueva obra** crea otra a partir del estilo actual (sin eje); se cambia de obra con el selector.

## Cámara (2.4)

- **Fotos horizontales automáticas**: la app queda en vertical y gira la foto según el acelerómetro
  (Ajustes → Cámara → Orientación: Automática / Siempre vertical / Siempre horizontal).
- **Rumbo (brújula)**: hacia dónde apunta la cámara, en grados y punto cardinal, y respecto al eje
  ("hacia PK ↑", "hacia PK ↓", "hacia lado Izq./Der."). Se activa en Información en la foto.
- **Nota rápida** (botón NOTA): texto solo para la siguiente foto; va en el stamp y en el PDF.
- **Botón de volumen** toma la foto (app Android, solo en la pestaña Cámara).
- **Mini mapa del eje** en la foto: tramo cercano, marcas de PK, tu posición, hacia dónde mira la cámara, norte y escala.

## Fotos, verificación y video (2.5)

- **Estampar fotos de la galería** (Galería → ＋): toma la fecha y el GPS guardados en la foto, calcula el PK y
  pone el stamp con la leyenda "Foto importada". Varias a la vez. En Android se pide el permiso
  "ubicación de fotos" porque el sistema borra el GPS sin él. Si la foto no trae GPS se estampa sin PK.
- **Código de autenticidad**: cada foto guarda su huella SHA-256 (código corto en el visor y el PDF; huella completa
  en la última página del reporte). **Verificar foto** dice si un archivo es idéntico al original.
  WhatsApp recomprime las fotos: para conservar la huella hay que mandarlas como "Documento".
- **Video con stamp** (modo VIDEO): stamp en vivo con hora, GPS y PK, con audio; MP4 cuando el teléfono lo permite
  (si no, WebM), 1280 px, máximo 10 min. Se guarda en Películas › ObraSnap.

## Diagnóstico

Config → Diagnóstico muestra versión, cámara, GPS, eje y último error.
**Ver registro / Compartir registro** guarda los últimos 400 eventos y errores para enviarlos a soporte.
En la app Android también se puede inspeccionar desde una PC con `chrome://inspect` (USB, modo desarrollador).
