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
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

        askPermissions();
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME);
    }

    // ── permisos de Android ──
    private void askPermissions() {
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.CAMERA);
        need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT <= 28) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        List<String> missing = new ArrayList<>();
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    private boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != REQ_PERMS) return;
        if (pendingWebPerm != null) {
            if (has(Manifest.permission.CAMERA)) pendingWebPerm.grant(pendingWebPerm.getResources());
            else pendingWebPerm.deny();
            pendingWebPerm = null;
        }
        if (pendingGeoCb != null) {
            pendingGeoCb.invoke(pendingGeoOrigin, has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION), false);
            pendingGeoCb = null;
        }
        // si la cámara se acaba de autorizar, que la página la vuelva a pedir
        web.evaluateJavascript("window.startCamera&&startCamera(true)", null);
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                if (has(Manifest.permission.CAMERA)) request.grant(request.getResources());
                else { pendingWebPerm = request; requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMS); }
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
            try { startActivityForResult(Intent.createChooser(i, "Elegir archivo"), REQ_FILE); }
            catch (ActivityNotFoundException e) { fileCallback = null; return false; }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCallback != null) {
            Uri[] r = null;
            if (result == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(r);
            fileCallback = null;
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

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.obrasnapBack?obrasnapBack():false", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
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
                String where = saveToMedia(f, m[0], m[1]);
                f.delete();
                return "ok:" + where;
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
    }

    private String saveToMedia(File src, String name, String mime) throws Exception {
        boolean image = mime.startsWith("image/");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            String rel = (image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/ObraSnap";
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri col = image ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = cr.insert(col, v);
            if (item == null) throw new Exception("no se pudo crear el archivo");
            try (OutputStream o = cr.openOutputStream(item); InputStream i = new FileInputStream(src)) { copy(i, o); }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(item, v, null, null);
            return rel;
        }
        // Android 9 o anterior
        File dir = new File(Environment.getExternalStoragePublicDirectory(image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS), "ObraSnap");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("sin permiso de almacenamiento");
        File out = new File(dir, name);
        try (OutputStream o = new FileOutputStream(out); InputStream i = new FileInputStream(src)) { copy(i, o); }
        MediaScannerConnection.scanFile(this, new String[]{out.getAbsolutePath()}, new String[]{mime}, null);
        return dir.getAbsolutePath();
    }

    private static void copy(InputStream i, OutputStream o) throws Exception {
        byte[] b = new byte[64 * 1024];
        int n;
        while ((n = i.read(b)) > 0) o.write(b, 0, n);
    }
}
