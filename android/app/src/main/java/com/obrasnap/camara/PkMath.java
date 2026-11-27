package com.obrasnap.camara;

/** Cálculo del PK igual que la app (index.html: toXY, projectOnRoute, pkFromAlong, fmtPK). Sin dependencias de Android para poder probarlo. */
final class PkMath {
    final double[] xs, ys, ds; final double[][] tbl; final double lat0, M, N, pk0, total; final boolean rev; final int round;
    PkMath(double[] xs, double[] ys, double[] ds, double[][] tbl, double lat0, double pk0, boolean rev, int round) {
        this.xs = xs; this.ys = ys; this.ds = ds; this.tbl = tbl; this.lat0 = lat0; this.pk0 = pk0; this.rev = rev; this.round = Math.max(1, round);
        double a = 6378137, e2 = 0.00669437999014, sn = Math.sin(Math.toRadians(lat0)), w = 1 - e2 * sn * sn;
        M = a * (1 - e2) / Math.pow(w, 1.5); N = a / Math.sqrt(w); total = ds[ds.length - 1];
    }
    /** {pk (ya redondeado), distancia al eje, 1 si va a la izquierda / 0 a la derecha, pk sin redondear} */
    double[] at(double lat, double lon) {
        double r = Math.PI / 180, px = lon * r * N * Math.cos(lat0 * r), py = lat * r * M;
        double best = Double.MAX_VALUE, along = 0, cross = 0;
        for (int i = 0; i < xs.length - 1; i++) {
            double dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i], l2 = dx * dx + dy * dy;
            double t = l2 > 0 ? ((px - xs[i]) * dx + (py - ys[i]) * dy) / l2 : 0;
            t = Math.max(0, Math.min(1, t));
            double d = Math.hypot(px - (xs[i] + t * dx), py - (ys[i] + t * dy));
            if (d < best) { best = d; along = ds[i] + t * (ds[i + 1] - ds[i]); cross = dx * (py - ys[i]) - dy * (px - xs[i]); }
        }
        double raw = pkFromAlong(along), pk = raw;
        if (round > 1) pk = Math.round(pk / round) * (double) round;
        return new double[]{pk, best, ((cross > 0) != rev) ? 1 : 0, raw};
    }
    double pkFromAlong(double along) {
        if (tbl != null && tbl.length >= 2) {
            int lo = 0, hi = tbl.length - 1;
            if (along <= tbl[0][0]) hi = 1;
            else if (along >= tbl[hi][0]) lo = hi - 1;
            else { while (hi - lo > 1) { int m = (lo + hi) >> 1; if (tbl[m][0] <= along) lo = m; else hi = m; } }
            double a0 = tbl[lo][0], p0 = tbl[lo][1], a1 = tbl[hi][0], p1 = tbl[hi][1];
            return p0 + (along - a0) * (p1 - p0) / (a1 - a0);
        }
        return pk0 + (rev ? total - along : along);
    }
    static String fmtPK(double m) {
        if (m < 0) return "-" + fmtPK(-m);
        long km = (long) Math.floor(m / 1000); long r = Math.round(m - km * 1000);
        if (r == 1000) { km++; r = 0; }
        return km + "+" + String.format(java.util.Locale.US, "%03d", r);
    }
}
