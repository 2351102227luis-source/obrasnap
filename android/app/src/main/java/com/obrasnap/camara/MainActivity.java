package com.obrasnap.camara;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Toast;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ObraSnap independiente: la app web (index.html y demás) va dentro del APK en assets/www
 * y se sirve desde https://appassets.androidplatform.net (origen seguro: cámara, GPS e IndexedDB funcionan).
 * Lo que WebView no hace solo (permisos, elegir archivos, guardar en Galería/Descargas, compartir,
 * botón atrás) se resuelve aquí y se expone a JavaScript como window.ObraSnapAndroid.
 */
public class MainActivity extends Activity {
    private static final String TAG = "ObraSnap";
    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_PERMS = 1, REQ_FILE = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPerm;
    private GeolocationPermissions.Callback pendingGeoCb;
    private String pendingGeoOrigin;
    private boolean jsReady = false;
    private volatile boolean volumeShutter = false; // la página lo activa solo en la pestaña Cámara
    private String pendingImport;   // llamada JS lista para entregar cuando la página esté cargada

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Permite inspeccionar la app desde chrome://inspect en una PC (USB) para depurar
        WebView.setWebContentsDebuggingEnabled(true);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0A1A0F"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100); // que el tamaño de letra del sistema no descuadre la interfaz

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                openExternalUri(u); // enlaces externos → navegador
                return true;
            }
        });
        web.setWebChromeClient(new Chrome());
        web.addJavascriptInterface(new Bridge(), "ObraSnapAndroid");

        cleanShareCache();
        askPermissions();
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME);
        handleIntent(getIntent());
    }

    // ── archivos .obrasnap recibidos (Abrir con / Compartir a ObraSnap) ──
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @SuppressWarnings("deprecation")
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        else if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("no se pudo abrir");
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[64 * 1024];
            int n, total = 0;
            while ((n = in.read(b)) > 0) {
                total += n;
                if (total > 20 * 1024 * 1024) throw new Exception("archivo demasiado grande");
                buf.write(b, 0, n);
            }
            String head = new String(buf.toByteArray(), 0, Math.min(buf.size(), 4096), "UTF-8");
            if (!head.contains("obrasnap-config")) {
                Toast.makeText(this, "Ese archivo no es una configuración de ObraSnap", Toast.LENGTH_LONG).show();
                return;
            }
            String b64 = Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP);
            String name = displayName(uri).replaceAll("[^\\w .()+-]", "_");
            pendingImport = "window.obrasnapImportB64&&obrasnapImportB64('" + b64 + "','" + name + "')";
            deliverImport();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo leer el archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        setIntent(new Intent()); // que no se vuelva a importar al girar o reabrir
    }

    private void deliverImport() {
        if (!jsReady || pendingImport == null) return;
        final String js = pendingImport;
        pendingImport = null;
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { String n = c.getString(0); if (n != null) return n; }
        } catch (Exception ignored) { }
        String p = uri.getLastPathSegment();
        return p != null ? p : "obra.obrasnap";
    }

    // ── permisos de Android ──
    private void askPermissions() {
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.CAMERA);
        need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT <= 28) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 29) need.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        List<String> missing = new ArrayList<>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    private void cleanShareCache() {
        File dir = new File(getCacheDir(), "share");
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long lim = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : fs) if (f.lastModified() < lim) f.delete();
    }

    private boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != REQ_PERMS) return;
        if (pendingWebPerm != null) { grantAllowed(pendingWebPerm); pendingWebPerm = null; }
        if (pendingGeoCb != null) {
            pendingGeoCb.invoke(pendingGeoOrigin, has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION), false);
            pendingGeoCb = null;
        }
        // si la cámara se acaba de autorizar, que la página la vuelva a pedir
        web.evaluateJavascript("window.startCamera&&startCamera(true)", null);
    }

    private class Chrome extends WebChromeClient {
        /** Sin el cartel gris de "video" mientras abre la cámara: imagen transparente (se ve el fondo negro). */
        @Override public Bitmap getDefaultVideoPoster() {
            Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.TRANSPARENT);
            return b;
        }
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                List<String> missing = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && !has(Manifest.permission.CAMERA)) missing.add(Manifest.permission.CAMERA);
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && !has(Manifest.permission.RECORD_AUDIO)) missing.add(Manifest.permission.RECORD_AUDIO);
                }
                if (missing.isEmpty()) grantAllowed(request);
                else { pendingWebPerm = request; requestPermissions(missing.toArray(new String[0]), REQ_PERMS); }
            });
        }
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) cb.invoke(origin, true, false);
            else {
                pendingGeoOrigin = origin; pendingGeoCb = cb;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMS);
            }
        }
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            String[] types = params.getAcceptTypes();
            i.setType(types.length > 0 && types[0] != null && !types[0].isEmpty() ? types[0] : "*/*");
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(Intent.createChooser(i, "Elegir archivo"), REQ_FILE); }
            catch (ActivityNotFoundException e) { fileCallback = null; return false; }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCallback != null) {
            List<Uri> uris = new ArrayList<>();
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null)
                    for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
                else if (data.getData() != null) uris.add(data.getData());
            }
            final ValueCallback<Uri[]> cb = fileCallback;
            fileCallback = null;
            if (uris.isEmpty()) { cb.onReceiveValue(null); return; }
            // copiar en segundo plano las fotos con sus datos GPS originales
            new Thread(() -> {
                Uri[] out = new Uri[uris.size()];
                for (int k = 0; k < uris.size(); k++) out[k] = withOriginalLocation(uris.get(k), k);
                runOnUiThread(() -> cb.onReceiveValue(out));
            }).start();
            return;
        }
        super.onActivityResult(code, result, data);
    }

    // ── ciclo de vida: liberar la cámara al salir, retomarla al volver ──
    @Override
    protected void onPause() {
        if (web != null) web.evaluateJavascript("window.obrasnapLifecycle&&obrasnapLifecycle('pause')", null);
        super.onPause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.obrasnapLifecycle&&obrasnapLifecycle('resume')", null);
    }
    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    // Botón de volumen = obturador (útil con guantes). Solo cuando la página lo pidió.
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        int k = ev.getKeyCode();
        if (volumeShutter && (k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || k == android.view.KeyEvent.KEYCODE_VOLUME_UP)) {
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0)
                web.evaluateJavascript("window.obrasnapShutter&&obrasnapShutter()", null);
            return true; // no cambia el volumen
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.obrasnapBack?obrasnapBack():false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    private void grantAllowed(PermissionRequest req) {
        List<String> ok = new ArrayList<>();
        for (String r : req.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && has(Manifest.permission.CAMERA)) ok.add(r);
            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && has(Manifest.permission.RECORD_AUDIO)) ok.add(r);
        }
        if (ok.isEmpty()) req.deny(); else req.grant(ok.toArray(new String[0]));
    }

    /**
     * Android 10+ borra el GPS de las fotos que se entregan a otras apps. Con ACCESS_MEDIA_LOCATION
     * y setRequireOriginal se obtiene el archivo completo; se copia a caché y se entrega esa copia.
     */
    private Uri withOriginalLocation(Uri uri, int idx) {
        if (Build.VERSION.SDK_INT < 29 || !has(Manifest.permission.ACCESS_MEDIA_LOCATION)) return uri;
        try {
            String type = getContentResolver().getType(uri);
            if (type == null || !type.startsWith("image/")) return uri;
            Uri media = uri;
            try { media = MediaStore.getMediaUri(this, uri); } catch (Exception ignored) { }
            if (media == null) media = uri;
            Uri orig = MediaStore.setRequireOriginal(media);
            File dir = new File(getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            String name = displayName(uri).replaceAll("[^\\w.-]", "_");
            File f = new File(dir, "import_" + idx + "_" + name);
            try (InputStream in = getContentResolver().openInputStream(orig); OutputStream o = new FileOutputStream(f)) {
                if (in == null) return uri;
                copy(in, o);
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        } catch (Exception e) {
            Log.w(TAG, "sin ubicación original: " + e.getMessage());
            return uri;
        }
    }

    private Uri mediaUri(String u) {
        if (u.startsWith("stage:"))
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(new File(getCacheDir(), "share"), u.substring(6)));
        Uri uri = Uri.parse(u);
        if ("file".equals(uri.getScheme())) // Android 9: entregar con FileProvider no aplica fuera de caché; se usa tal cual
            return uri;
        return uri;
    }

    private void openExternalUri(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (ActivityNotFoundException e) { Log.w(TAG, "sin app para " + u); }
    }

    // ── puente JavaScript ↔ Android ──
    private class Bridge {
        private final Map<String, File> pending = new HashMap<>();
        private final Map<String, String[]> meta = new HashMap<>();
        private int seq = 0;

        @JavascriptInterface
        public synchronized String begin(String name, String mime) {
            try {
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                File f = new File(dir, safe);
                new FileOutputStream(f).close();
                String id = "t" + (++seq);
                pending.put(id, f);
                meta.put(id, new String[]{safe, mime});
                return id;
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public synchronized void append(String id, String b64) {
            File f = pending.get(id);
            if (f == null) return;
            try (FileOutputStream o = new FileOutputStream(f, true)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
            catch (Exception e) { Log.e(TAG, "append", e); }
        }

        /** mode "save": fotos → Galería (Pictures/ObraSnap), lo demás → Descargas/ObraSnap. mode "share": menú Compartir. */
        @JavascriptInterface
        public synchronized String finish(String id, String mode) {
            File f = pending.remove(id);
            String[] m = meta.remove(id);
            if (f == null || m == null) return "err:transferencia desconocida";
            try {
                if ("share".equals(mode)) {
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(m[1]);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(() -> startActivity(Intent.createChooser(send, "Compartir")));
                    return "ok:compartir";
                }
                if ("stage".equals(mode)) return "ok:stage:" + f.getName(); // queda en caché para compartir varios
                String where = saveToMedia(f, m[0], m[1]);
                f.delete();
                return "ok:" + where;   // content://… (o file://… en Android 9): sirve para abrir o borrar después
            } catch (Exception e) {
                Log.e(TAG, "finish", e);
                return "err:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public String appInfo() {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                JSONObject o = new JSONObject();
                o.put("versionName", pi.versionName);
                o.put("versionCode", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
                o.put("android", Build.VERSION.RELEASE);
                o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                PackageInfo wv = WebViewCompat.getCurrentWebViewPackage(MainActivity.this);
                o.put("webview", wv != null ? wv.versionName : "");
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void openExternal(String url) { runOnUiThread(() -> openExternalUri(Uri.parse(url))); }

        /** Cambia el ícono de la app activando uno de los dos alias del lanzador ("arena" o "senal"). */
        @JavascriptInterface
        public void setIcon(String theme) {
            String pkg = getPackageName();
            android.content.ComponentName arena = new android.content.ComponentName(pkg, pkg + ".LauncherArena");
            android.content.ComponentName senal = new android.content.ComponentName(pkg, pkg + ".LauncherSenal");
            PackageManager pm = getPackageManager();
            boolean s = "senal".equals(theme);
            // primero se activa el nuevo y luego se desactiva el otro, para que nunca falte el ícono
            pm.setComponentEnabledSetting(s ? senal : arena, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(s ? arena : senal, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }

        @JavascriptInterface
        public void setVolumeShutter(boolean on) { volumeShutter = on; }

        /** Borra del teléfono un archivo que guardó la app (foto o video). */
        @JavascriptInterface
        public String deleteMedia(String u) {
            try {
                Uri uri = Uri.parse(u);
                if ("file".equals(uri.getScheme())) {
                    File f = new File(uri.getPath());
                    boolean ok = !f.exists() || f.delete();
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{f.getAbsolutePath()}, null, null);
                    return ok ? "ok" : "err:no se pudo borrar";
                }
                int n = getContentResolver().delete(uri, null, null);
                return n > 0 ? "ok" : "ok:ya no existía";
            } catch (SecurityException e) {
                return "err:Android no permite borrarlo (¿se reinstaló la app?)";
            } catch (Exception e) { return "err:" + e.getMessage(); }
        }

        /** Abre un archivo del teléfono en su app (videos → reproductor). */
        @JavascriptInterface
        public String openMedia(String u, String mime) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(mediaUri(u), mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> { try { startActivity(i); } catch (Exception e) { Log.w(TAG, "abrir", e); } });
                return "ok";
            } catch (Exception e) { return "err:" + e.getMessage(); }
        }

        /** Comparte varios archivos: "stage:<nombre>" (preparados en caché) o content://… ya guardados. */
        @JavascriptInterface
        public String shareMany(String json, String mime) {
            try {
                org.json.JSONArray a = new org.json.JSONArray(json);
                ArrayList<Uri> uris = new ArrayList<>();
                for (int k = 0; k < a.length(); k++) uris.add(mediaUri(a.getString(k)));
                if (uris.isEmpty()) return "err:nada que compartir";
                Intent send;
                if (uris.size() == 1) { send = new Intent(Intent.ACTION_SEND); send.putExtra(Intent.EXTRA_STREAM, uris.get(0)); }
                else { send = new Intent(Intent.ACTION_SEND_MULTIPLE); send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); }
                send.setType(mime);
                android.content.ClipData clip = android.content.ClipData.newRawUri("", uris.get(0));
                for (int k = 1; k < uris.size(); k++) clip.addItem(new android.content.ClipData.Item(uris.get(k)));
                send.setClipData(clip);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Compartir")));
                return "ok";
            } catch (Exception e) { return "err:" + e.getMessage(); }
        }

        /** La página avisa que ya cargó: entregar el archivo .obrasnap que esté esperando. */
        @JavascriptInterface
        public void ready() { jsReady = true; deliverImport(); }
    }

    private String saveToMedia(File src, String name, String mime) throws Exception {
        boolean image = mime.startsWith("image/"), video = mime.startsWith("video/");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            String rel = (image ? Environment.DIRECTORY_PICTURES : video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS) + "/ObraSnap";
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri col = image ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = cr.insert(col, v);
            if (item == null) throw new Exception("no se pudo crear el archivo");
            try (OutputStream o = cr.openOutputStream(item); InputStream i = new FileInputStream(src)) { copy(i, o); }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(item, v, null, null);
            return item.toString();
        }
        // Android 9 o anterior
        File dir = new File(Environment.getExternalStoragePublicDirectory(image ? Environment.DIRECTORY_PICTURES : video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS), "ObraSnap");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("sin permiso de almacenamiento");
        File out = new File(dir, name);
        try (OutputStream o = new FileOutputStream(out); InputStream i = new FileInputStream(src)) { copy(i, o); }
        MediaScannerConnection.scanFile(this, new String[]{out.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(out).toString();
    }

    private static void copy(InputStream i, OutputStream o) throws Exception {
        byte[] b = new byte[64 * 1024];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
    }
}
