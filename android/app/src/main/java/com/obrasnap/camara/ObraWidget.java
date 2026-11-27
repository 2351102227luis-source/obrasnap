package com.obrasnap.camara;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Widget de ObraSnap: obra, PK y lado, corte o terraplén de la sección, actividad y fotos de hoy.
 * Los datos los manda la app (bridge updateWidget) y, si está prendido el PK en la barra, el servicio del PK en vivo.
 * Al agrandarlo (alto) muestra el resumen de hoy por actividad con lo que falta por enviar.
 */
public class ObraWidget extends AppWidgetProvider {
    static final String PREFS = "obrasnap_widget";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) update(c, m, id); }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { update(c, m, id); }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, ObraWidget.class));
            for (int id : ids) update(c, m, id);
        } catch (Exception e) { Log.e("ObraSnapWidget", "refresh", e); }
    }

    private static PendingIntent go(Context c, String what, int req) {
        Intent i = new Intent(c, MainActivity.class).setAction("com.obrasnap.camara.GO." + what)
                .putExtra("go", what).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(c, req, i, fl);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_obra);
        JSONObject d, live;
        try { d = new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", "{}")); } catch (Exception e) { d = new JSONObject(); }
        try { live = new JSONObject(c.getSharedPreferences(PkService.PREFS, Context.MODE_PRIVATE).getString("live", "{}")); } catch (Exception e) { live = new JSONObject(); }
        // PK: el más reciente entre la app y el servicio
        JSONObject src = live.optLong("t", 0) > d.optLong("t", 0) && live.has("pk") ? live : d;
        String pk = src.optString("pk", "");
        long t = src.optLong("t", 0);
        v.setTextViewText(R.id.w_obra, d.optString("obra", "ObraSnap"));
        v.setTextViewText(R.id.w_pk, pk.isEmpty() ? "PK --+---" : "PK " + pk);
        String side = src.optString("side", "");
        String hora = t > 0 ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t)) : "";
        v.setTextViewText(R.id.w_side, side.isEmpty() ? (pk.isEmpty() ? "Abre ObraSnap para ver tu PK" : "") : side + (hora.isEmpty() ? "" : " · " + hora));
        String sec = src.optString("sec", d.optString("sec", ""));
        v.setTextViewText(R.id.w_sec, sec);
        v.setViewVisibility(R.id.w_sec, sec.isEmpty() ? View.GONE : View.VISIBLE);
        int fotos = d.optInt("fotos", 0);
        String act = d.optString("act", "");
        v.setTextViewText(R.id.w_info, (act.isEmpty() ? "" : "✎ " + act + " · ") + fotos + (fotos == 1 ? " foto hoy" : " fotos hoy"));
        // resumen por actividad (solo si el widget está alto)
        Bundle o = m.getAppWidgetOptions(id);
        int minH = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;
        JSONArray acts = d.optJSONArray("acts");
        StringBuilder sb = new StringBuilder();
        if (acts != null) for (int i = 0; i < acts.length(); i++) {
            JSONObject a = acts.optJSONObject(i); if (a == null) continue;
            int n = a.optInt("c"), p = a.optInt("p");
            if (sb.length() > 0) sb.append('\n');
            sb.append(a.optString("n")).append("  ·  ").append(n).append(p > 0 ? "  ·  " + p + " por enviar" : "  ✓ enviado");
        }
        boolean tall = minH >= 170 && sb.length() > 0;
        v.setTextViewText(R.id.w_resumen, sb.toString());
        v.setViewVisibility(R.id.w_resumen, tall ? View.VISIBLE : View.GONE);
        // miniatura de la última foto
        Bitmap bmp = null;
        String th = d.optString("thumb", "");
        if (th.startsWith("data:image")) {
            try { byte[] b = Base64.decode(th.substring(th.indexOf(',') + 1), Base64.DEFAULT); bmp = BitmapFactory.decodeByteArray(b, 0, b.length); } catch (Exception ignored) { }
        }
        if (bmp != null) { v.setImageViewBitmap(R.id.w_thumb, bmp); v.setViewVisibility(R.id.w_thumb, View.VISIBLE); }
        else v.setViewVisibility(R.id.w_thumb, View.GONE);
        v.setOnClickPendingIntent(R.id.w_root, go(c, "abrir", 10));
        v.setOnClickPendingIntent(R.id.w_btn_foto, go(c, "foto", 11));
        v.setOnClickPendingIntent(R.id.w_btn_sec, go(c, "seccion", 12));
        v.setOnClickPendingIntent(R.id.w_btn_env, go(c, "enviar", 13));
        m.updateAppWidget(id, v);
    }
}
