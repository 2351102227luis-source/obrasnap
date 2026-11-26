package com.obrasnap.camara;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * PK en la barra de notificaciones, aunque la app esté cerrada.
 * Lee el GPS cada ~5 s (o al moverse 3 m) y calcula el PK con el mismo método de la app:
 * proyección sobre el eje en un plano local del elipsoide WGS84 y la tabla de marcas del KML (o PK inicial + sentido).
 * El eje y los ajustes los manda la página (MainActivity.setPkNotify / updatePkAxis) y se guardan en preferencias.
 */
public class PkService extends Service {
    static final String TAG = "ObraSnapPK";
    static final String PREFS = "obrasnap_pk";
    static final String ACTION_STOP = "com.obrasnap.camara.PK_STOP";
    static final String ACTION_UPDATE = "com.obrasnap.camara.PK_UPDATE";
    static final String CHANNEL = "pk";
    static final int NOTIF_ID = 7;
    static volatile boolean running = false;

    private LocationManager lm;
    private Location last;
    // eje
    private PkMath pm;
    private String obra = "", sideFmt = "izqder";

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { last = l; show(); }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { show(); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(a)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("on", false).apply();
            stopSelfNow();
            return START_NOT_STICKY;
        }
        loadAxis();
        startFg(build("Buscando GPS…", obra.isEmpty() ? "ObraSnap" : obra));
        running = true;
        if (lm == null) startGps();
        show();
        return START_STICKY;
    }

    private void stopSelfNow() {
        running = false;
        if (lm != null) { try { lm.removeUpdates(listener); } catch (Exception ignored) { } lm = null; }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        running = false;
        if (lm != null) { try { lm.removeUpdates(listener); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 3f, listener, Looper.getMainLooper());
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 10f, listener, Looper.getMainLooper());
            Location k = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (k != null && System.currentTimeMillis() - k.getTime() < 120000) last = k;
        } catch (SecurityException e) { Log.e(TAG, "gps", e); }
    }

    // ── eje y ajustes (los manda la página) ──
    private void loadAxis() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        pm = null;
        try {
            JSONObject o = new JSONObject(p.getString("axis", "{}"));
            obra = o.optString("obra", "");
            sideFmt = o.optString("sideFmt", "izqder");
            JSONArray pts = o.optJSONArray("pts");
            if (pts == null || pts.length() < 2) return;
            int n = pts.length(); double[] xs = new double[n], ys = new double[n], ds = new double[n];
            for (int i = 0; i < n; i++) { JSONArray q = pts.getJSONArray(i); xs[i] = q.getDouble(0); ys[i] = q.getDouble(1); ds[i] = q.getDouble(2); }
            double[][] tbl = null;
            JSONArray t = o.optJSONArray("tbl");
            if (t != null && t.length() >= 2) {
                tbl = new double[t.length()][2];
                for (int i = 0; i < t.length(); i++) { tbl[i][0] = t.getJSONArray(i).getDouble(0); tbl[i][1] = t.getJSONArray(i).getDouble(1); }
            }
            pm = new PkMath(xs, ys, ds, tbl, o.getDouble("lat0"), o.optDouble("pk0", 0), o.optBoolean("rev", false), o.optInt("round", 1));
        } catch (Exception e) { Log.e(TAG, "eje", e); pm = null; }
    }

    private void show() {
        String title, text;
        if (pm == null) { title = "Sin eje cargado"; text = "Carga el eje de la obra en ObraSnap"; }
        else if (last == null) { title = "Buscando GPS…"; text = obra.isEmpty() ? "ObraSnap" : obra; }
        else {
            double[] q = pm.at(last.getLatitude(), last.getLongitude());
            double best = q[1];
            title = "PK " + PkMath.fmtPK(q[0]);
            String side = q[2] > 0 ? "Izq." : "Der.";
            long d = Math.round(best);
            String s;
            if (best > 500) s = (best >= 1000 ? String.format(java.util.Locale.US, "%.1f km", best / 1000) : d + " m") + " del eje · fuera de ruta";
            else if (d < 1) s = "sobre el eje";
            else s = "lild".equals(sideFmt) ? (side.startsWith("I") ? "LI " : "LD ") + d + " m" : d + " m " + side;
            if (best <= 500) { title += " · " + s; text = obra.isEmpty() ? "ObraSnap" : obra; }
            else text = s + (obra.isEmpty() ? "" : " · " + obra);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        try { nm.notify(NOTIF_ID, build(title, text)); } catch (SecurityException ignored) { }
    }

    private Notification build(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "PK en la barra", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Muestra el PK de tu posición mientras caminas");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 1, open, fl);
        PendingIntent pStop = PendingIntent.getService(this, 2, new Intent(this, PkService.class).setAction(ACTION_STOP), fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_pk).setContentTitle(title).setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setContentIntent(pOpen)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "Detener", pStop).build());
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private void startFg(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) { Log.e(TAG, "fg", e); stopSelf(); }
    }
}
