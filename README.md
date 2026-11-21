# ObraSnap

Cámara de obra que imprime en cada foto los datos de PK, GPS, fecha y actividad. Funciona como página web (GitHub Pages) y como app de Android independiente.

- Web: https://2351102227luis-source.github.io/obrasnap/
- APK: https://2351102227luis-source.github.io/obrasnap/descargas/ObraSnap.apk

## Dónde está cada cosa

| Archivo | Qué es |
|---|---|
| `index.html` | **Toda la app** (pantallas, cámara, datos impresos en la foto, PK, galería, PDF). Es lo que se edita casi siempre. |
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

## La app arranca en blanco

No trae datos de ninguna obra. Cada obra se configura en Config → Obras, o se importa su archivo `.obrasnap`
(nombre, eje, actividades, formatos, estilo, logo y nombre de archivos).

## Obras (configuración compartible)

Config → **Obras**: cada obra guarda nombre, actividades frecuentes, formatos (PK, fecha, hora, coordenadas),
estilo y posiciones de los datos en la foto, ajustes de cámara, logo y —opcional— el eje con su calibración.

- **Compartir configuración de esta obra** genera un archivo `NombreDeObra.obrasnap` (JSON) para mandar por WhatsApp/correo.
- En la app Android basta con tocar el archivo y elegir **Abrir con ObraSnap**; en la web, **Importar configuración**.
- Si ya existe una obra con el mismo nombre, se reemplaza su configuración (las fotos no se tocan).
- **+ Nueva obra** crea otra a partir del estilo actual (sin eje); se cambia de obra con el selector.

## Cámara (2.4)

- **Fotos horizontales automáticas**: la app queda en vertical y gira la foto según el acelerómetro
  (Ajustes → Cámara → Orientación: Automática / Siempre vertical / Siempre horizontal).
- **Rumbo (brújula)**: hacia dónde apunta la cámara, en grados y punto cardinal, y respecto al eje
  ("hacia PK ↑", "hacia PK ↓", "hacia lado Izq./Der."). Se activa en Información en la foto.
- **Nota rápida** (botón NOTA): texto solo para la siguiente foto; va en los datos de la foto y en el PDF.
- **Botón de volumen** toma la foto (app Android, solo en la pestaña Cámara).
- **Mini mapa del eje** en la foto: tramo cercano, marcas de PK, tu posición, hacia dónde mira la cámara, norte y escala.

## Fotos, verificación y video (2.5)

- **Poner datos a fotos de la galería** (Galería → ＋): toma la fecha y el GPS guardados en la foto, calcula el PK y
  le pone los datos con la leyenda "Foto importada". Varias a la vez. En Android se pide el permiso
  "ubicación de fotos" porque el sistema borra el GPS sin él. Si la foto no trae GPS se le ponen los datos sin PK.
- **Código de autenticidad**: cada foto guarda su huella SHA-256 (código corto en el visor y el PDF; huella completa
  en la última página del reporte). (Hasta la 2.15, "Verificar foto" comparaba un archivo con el original.)
  WhatsApp recomprime las fotos: para conservar la huella hay que mandarlas como "Documento".
- **Video con datos en la foto** (modo VIDEO): datos en la foto en vivo con hora, GPS y PK, con audio; MP4 cuando el teléfono lo permite
  (si no, WebM), 1280 px, máximo 10 min. Se guarda en Películas › ObraSnap.

## Nombre de los archivos (2.6)

Ajustes → **Nombre de los archivos**. El formato es parte de la obra (viaja en el `.obrasnap`), así todo el equipo nombra igual.
Etiquetas: `{AAMMDD}` `{AAAAMMDD}` `{HHMM}` `{HHMMSS}` `{PK}` (1+250) `{PKN}` (001250) `{N}` (consecutivo) `{INI}` (iniciales del teléfono)
`{OBRA}` `{ACT}` `{LADO}` `{AAAA}` `{AA}` `{MM}` `{DD}` `{HH}` `{mm}` `{ss}`. Por defecto: `{AAMMDD}_{PK}_{HHMM}_{N}` → `261120_1+250_0915_001`.
El consecutivo es por obra y por teléfono (reinicia cada día o sigue siempre); las iniciales evitan nombres repetidos entre compañeros.

## Versión 2.19

- Texto de la foto parejo: una sola letra y un solo color; texto 12 pt, obra 13 pt y PK 14 pt (negritas, arriba). Config › Letra.
- Posición de los elementos incluye el mini mapa.
- Estilo: sin alineación (automática según la esquina) y separación del borde 50 % por omisión; muestras de fondo con nombre.

## Versión 2.18

- Zoom (1× · 2 · 3) en una columna sobre la foto, a la derecha (a la izquierda si los datos o el mini mapa van a media altura a la derecha).
- 16:9 a pantalla completa: los íconos de arriba quedan sobre la imagen; 4:3 y 1:1 siguen centradas.
- Nivel: línea al centro que se pone amarilla cuando el teléfono está derecho (Cámara › Nivel).
- Miniatura de la última foto con borde blanco.

## Versión 2.17

- Controles de la cámara y pestañas un poco más chicos: la foto gana alto (16:9 crece de ~318 a ~345 pt de ancho).
- La precisión del GPS (±m) ya no va en la foto.
- Cambio de pantalla con fundido suave; en el APK la barra de hora/batería y la de Android toman el color de cada pantalla.

## Versión 2.16 (limpieza)

- Letras del PK e información: Técnica, Condensada y Muy legible. Fondos: Transparente, Negro, Arena, Blanco y Amarillo.
- Los datos en la foto ya no llevan borde ni sombra.
- Un solo texto libre (si había dos, se juntan). El rumbo de brújula ya no va en la foto.
- Apariencia Arena / Señalética solo cambia los colores; el ícono queda fijo (si estaba cambiado, regresa al normal).
- Se quitan "Verificar foto" y los botones repetidos de Diagnóstico. El código SHA-256 sigue en el reporte PDF.
- Las obras y ajustes anteriores se convierten solos a las opciones que quedan.

## Versión 2.15

- **Capas desde el mapa**: botón de capas en los controles; prende/apaga capas y capas internas del plano sin salir.
- **Tocar el mapa** (fuera de Medir): tarjeta con nombre, N/E/Z, PK y lado, lat/lon y distancia desde ti; botones Ir a, Medir desde aquí y Copiar. El doble toque sigue acercando.
- **Más zoom**: hasta 100 px por metro (1 cm por píxel); escala en cm y marcas de PK cada 10/20 m de cerca.

## Versión 2.14

- **Capas y planos** (Config › Obra): eje principal + tres grupos con 👁, imán (si se engancha al medir) y ⋮ (ver, renombrar, eliminar):
  - **KMZ / KML**.
  - **CAD (DXF)**: líneas, polilíneas con curvas, arcos, círculos, puntos, textos y bloques; cada capa del plano se prende o apaga. DWG no (guardar como DXF). Coordenadas UTM o geográficas; las locales todavía no.
  - **Puntos (CSV)**: P, N, E, Z, D en cualquier orden, con vista previa para confirmar columnas.
- **Zona UTM** de la obra (14 N por defecto), viaja en el archivo `.obrasnap`.
- **Medir**: modo **Área** (m² / ha y perímetro); 2 puntos con **rumbo topográfico, azimut, ΔZ y pendiente**; coordenadas UTM del punto.

## Versión 2.13

- **Config** en tres grupos: **App** (Datos en la foto, Cámara) · **Obra** (Actividades, KMZ / KML, Obras) · **Sistema** (Apariencia, Versiones, Diagnóstico). El estado del GPS está en Diagnóstico.
- **Redondeo del PK** (Datos en la foto › Formatos): exacto, 5, 10 o 20 m al más cercano; se guarda también el PK exacto. Opcional para el video.
- **KMZ / KML**: eje principal (da el PK) + lista de **capas auxiliares** con ojo (mapa y mini mapa), renombrar y quitar. Viajan en el archivo de la obra. En el Mapa, "Cargar KMZ" pregunta si es eje o capa.
- **Medir** (regla en el Mapa): 2 puntos, tramo e "ir a punto" desde tu posición; se engancha a puntos, vértices y líneas; dejar el dedo muestra una **lupa**. Lista "Puntos del plano" con buscador. Dato opcional en la foto: "a 38 m de BN-12".
- **Galería**: 3 columnas por día, selección múltiple (toque largo) para compartir, descargar o borrar. Visor con pellizco, doble toque, deslizar y bajar para cerrar. La miniatura de la cámara abre la última foto.
- **Videos** en la galería (abren el reproductor del teléfono). Borrar en la app también borra la copia del teléfono.
- **Cámara**: zoom con pellizco (de la cámara o digital), enfoque al tocar y flash si el teléfono lo permite; sus funciones se ven en Diagnóstico. Pantalla negra "Abriendo cámara…".

## Versión 2.12

- **Config** en tres grupos: **App** (Datos en la foto, Cámara, GPS, Apariencia) · **Obra** (Actividades, Eje, Obras) · **Soporte** (Versiones, Diagnóstico).
- **Datos en la foto** (antes "stamp"): letras independientes para el PK y la información (8 tipografías incluidas), tamaño del PK y opacidad del logo.
- **Eje**: eje principal (da el PK) + **capa auxiliar** (proyecto dibujado: líneas, polígonos y puntos con sus colores) que se ve en el mapa y el mini mapa; puede ir en el archivo de la obra.
- **Cámara**: arriba GPS, actividad y nota; abajo solo FOTO y VIDEO.

## Menús (2.7)

**Ajustes** (⚙ en la cámara) y **Config** son menús: cada renglón muestra el valor actual y abre su propia pantalla
("‹ Ajustes" / "‹ Config" o el botón atrás para regresar). Los temas de Ajustes están en `SETTINGS_SCHEMA`
y los de Config en `CFG_MENU` (index.html).

## Mapa (2.11)

Controles: **+ / −**, **◎ mi ubicación** (centra y sigue al caminar; se suelta al mover el mapa), **⤢ ver todo el eje**,
pellizco con dos dedos (amplía donde están los dedos), doble toque para acercar y escala en la esquina.

## Diagnóstico

Config → Diagnóstico muestra versión, cámara, GPS, eje y último error.
**Ver registro / Compartir registro** guarda los últimos 400 eventos y errores para enviarlos a soporte.
En la app Android también se puede inspeccionar desde una PC con `chrome://inspect` (USB, modo desarrollador).
