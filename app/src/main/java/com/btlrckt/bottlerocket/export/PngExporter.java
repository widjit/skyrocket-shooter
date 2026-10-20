package com.btlrckt.bottlerocket.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLCapture;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.btlrckt.bottlerocket.model.BasePlanItem;
import com.btlrckt.bottlerocket.model.DefendedAsset;
import com.btlrckt.bottlerocket.model.EngagementAuthorityZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.PlanColors;
import com.btlrckt.bottlerocket.model.PointDefensePlan;
import com.btlrckt.bottlerocket.model.PointItem;
import com.btlrckt.bottlerocket.model.PolygonItem;
import com.btlrckt.bottlerocket.model.SensorItem;
import com.btlrckt.bottlerocket.model.ThreatCorridor;
import com.btlrckt.bottlerocket.settings.UnitSystem;
import com.btlrckt.bottlerocket.util.UnitConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exports the current plan as a single PNG briefing image:
 * classification banner, title block, the map view (captured from ATAK's renderer with all
 * BTLRCKT overlays), legend, item summary, classification banner.
 *
 * If the map cannot be captured (no GL surface, capture error, timeout, or a blank frame) the
 * map area is replaced by a schematic plan view (north-up, scale bar) drawn from plan data.
 */
public final class PngExporter {
    private static final String TAG = "BtlrcktPngExport";
    private static final long CAPTURE_TIMEOUT_MS = 6000;
    private static final int SCHEMATIC_WIDTH = 1600, SCHEMATIC_HEIGHT = 1200;

    public interface Callback {
        /** Called on the main thread. {@code usedMapCapture} is false when the schematic was used. */
        void onExported(File file, boolean usedMapCapture);

        /** Called on the main thread. */
        void onFailed(String reason);
    }

    private final MapView mapView;
    private final PointDefensePlan plan;
    private final UnitSystem unit;
    private final int decimals;
    private final File outDir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean done = new AtomicBoolean(false);
    /** R9: locally hidden UIDs (view only; they still count in every analysis). */
    private java.util.Set<String> hiddenUids = java.util.Collections.emptySet();
    private boolean includeHidden = true;

    /** @param planSnapshot a deep copy; it is read on a background thread. */
    public PngExporter(MapView mapView, PointDefensePlan planSnapshot, UnitSystem unit, int decimals, File outDir) {
        this.mapView = mapView;
        this.plan = planSnapshot;
        this.unit = unit;
        this.decimals = decimals;
        this.outDir = outDir;
    }

    /**
     * R9: hidden items are omitted from the schematic unless {@code include} is true. The live map
     * capture already reflects the overlay (which skips hidden items). A note is always added.
     */
    public PngExporter setHiddenUids(java.util.Set<String> uids, boolean include) {
        this.hiddenUids = uids == null ? java.util.Collections.<String>emptySet() : new java.util.HashSet<>(uids);
        this.includeHidden = include;
        return this;
    }

    private boolean skip(BasePlanItem item) {
        return !includeHidden && hiddenUids.contains(item.uid);
    }

    /** Starts the export; returns immediately. Call on the main thread. */
    public void export(final Callback cb) {
        GLMapSurface surface = null;
        try {
            surface = mapView == null ? null : mapView.getGLSurface();
        } catch (Throwable t) {
            Log.w(TAG, "No GL surface", t);
        }
        if (surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            finishAsync(null, cb);
            return;
        }
        final GLMapSurface s = surface;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "Map capture timed out; using schematic");
                finishAsync(null, cb);
            }
        }, CAPTURE_TIMEOUT_MS);
        try {
            new GLCapture(s) {
                @Override
                protected void draw() {
                    GLMapView view = s.getGLMapView();
                    if (view != null) view.render();
                }
            }.capture(new GLCapture.CaptureCallback() {
                @Override
                public void onCaptureStarted(GLCapture c) {
                }

                @Override
                public void onCaptureComplete(GLCapture c, Bitmap bmp) {
                    // Already on GLCapture's worker thread.
                    if (bmp != null && !isBlank(bmp)) {
                        finish(bmp, cb);
                    } else {
                        Log.w(TAG, "Map capture was blank; using schematic");
                        if (bmp != null) bmp.recycle();
                        finish(null, cb);
                    }
                }

                @Override
                public void onCaptureError(GLCapture c, Throwable t) {
                    Log.w(TAG, "Map capture failed; using schematic", t);
                    finishAsync(null, cb);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Map capture unavailable; using schematic", t);
            finishAsync(null, cb);
        }
    }

    private void finishAsync(final Bitmap map, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                finish(map, cb);
            }
        }, "btlrckt-png-export").start();
    }

    /** Composes and writes the image exactly once (capture vs. timeout race). */
    private void finish(Bitmap map, final Callback cb) {
        if (!done.compareAndSet(false, true)) {
            if (map != null) map.recycle();
            return;
        }
        main.removeCallbacksAndMessages(null);
        final boolean captured = map != null;
        Bitmap out = null;
        try {
            out = compose(map);
            if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("Cannot create " + outDir);
            final File file = new File(outDir, PngExportModel.fileName(plan, System.currentTimeMillis()));
            try (OutputStream os = new FileOutputStream(file)) {
                if (!out.compress(Bitmap.CompressFormat.PNG, 100, os)) throw new IOException("PNG encode failed");
            }
            Log.i(TAG, "Exported " + file + " (map capture: " + captured + ")");
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onExported(file, captured);
                }
            });
        } catch (final Throwable t) {
            Log.e(TAG, "PNG export failed", t);
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onFailed(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                }
            });
        } finally {
            if (map != null) map.recycle();
            if (out != null) out.recycle();
        }
    }

    /** True when the frame is fully transparent or a single flat color (renderer drew nothing). */
    static boolean isBlank(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        if (w <= 0 || h <= 0) return true;
        int first = b.getPixel(0, 0);
        boolean flat = true;
        for (int iy = 1; iy <= 15; iy++) {
            for (int ix = 1; ix <= 15; ix++) {
                int px = b.getPixel(ix * (w - 1) / 16, iy * (h - 1) / 16);
                if (Color.alpha(px) > 0 && px != first) flat = false;
                if (!flat) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- compose

    private Bitmap compose(Bitmap map) {
        int width = map != null ? map.getWidth() : SCHEMATIC_WIDTH;
        int mapHeight = map != null ? map.getHeight() : SCHEMATIC_HEIGHT;
        float k = Math.max(1f, width / 1600f);
        float banner = 44 * k, header = 112 * k, line = 30 * k, pad = 24 * k;

        List<String> summary = PngExportModel.summaryLines(plan, unit, decimals);
        List<Object[]> legend = PngExportModel.legend(plan);
        int perRow = Math.max(1, (int) ((width - 2 * pad) / (320 * k)));
        int legendRows = legend.isEmpty() ? 0 : (legend.size() + perRow - 1) / perRow;
        float footer = pad + legendRows * line + (legendRows > 0 ? pad / 2 : 0) + summary.size() * line + pad;
        int height = Math.round(banner + header + mapHeight + footer + banner);

        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF15181C);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);

        String cls = plan.classification == null || plan.classification.isEmpty()
                ? PointDefensePlan.DEFAULT_CLASSIFICATION : plan.classification.toUpperCase(Locale.US);
        drawBanner(c, cls, 0, width, banner, text, k);

        float y = banner;
        text.setFakeBoldText(true);
        text.setTextSize(36 * k);
        c.drawText(plan.name == null ? "" : plan.name, pad, y + 46 * k, text);
        text.setFakeBoldText(false);
        text.setTextSize(22 * k);
        text.setColor(0xFFB0B8C0);
        String meta = "BOTTLE ROCKET (BTL RCKT)  |  " + PngExportModel.dtg(System.currentTimeMillis())
                + (plan.author == null || plan.author.isEmpty() ? "" : "  |  " + plan.author)
                + "  |  " + plan.totalItems() + " items"
                + (map == null ? "  |  SCHEMATIC (map imagery not captured)" : "")
                + (hiddenUids.isEmpty() ? "" : "  |  " + hiddenUids.size() + " hidden ("
                        + (includeHidden && map == null ? "drawn" : "view only") + "; included in analysis)");
        c.drawText(meta, pad, y + 86 * k, text);
        y += header;

        if (map != null) {
            c.drawBitmap(map, 0, y, null);
        } else {
            drawSchematic(c, new RectF(0, y, width, y + mapHeight), k);
        }
        y += mapHeight + pad;

        // Legend
        Paint swatch = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(22 * k);
        for (int i = 0; i < legend.size(); i++) {
            float lx = pad + (i % perRow) * 320 * k, ly = y + (i / perRow) * line;
            swatch.setColor((Integer) legend.get(i)[1] | 0xFF000000);
            c.drawRect(lx, ly + 4 * k, lx + 22 * k, ly + 24 * k, swatch);
            c.drawText((String) legend.get(i)[0], lx + 32 * k, ly + 22 * k, text);
        }
        y += legendRows * line + (legendRows > 0 ? pad / 2 : 0);

        // Summary
        for (String s : summary) {
            boolean heading = !s.startsWith(" ");
            text.setFakeBoldText(heading);
            text.setColor(heading ? Color.WHITE : 0xFFC8D0D8);
            c.drawText(s, pad, y + 22 * k, text);
            y += line;
        }
        text.setFakeBoldText(false);
        drawBanner(c, cls, height - banner, width, banner, text, k);
        return out;
    }

    private static void drawBanner(Canvas c, String cls, float top, int width, float h, Paint text, float k) {
        Paint bg = new Paint();
        bg.setColor(bannerColor(cls));
        c.drawRect(0, top, width, top + h, bg);
        text.setColor(cls.startsWith("TOP SECRET") ? Color.BLACK : Color.WHITE);
        text.setFakeBoldText(true);
        text.setTextSize(24 * k);
        float tw = text.measureText(cls);
        c.drawText(cls, (width - tw) / 2f, top + h / 2f + 9 * k, text);
        text.setFakeBoldText(false);
    }

    /** Standard US banner colors by marking prefix. */
    static int bannerColor(String cls) {
        String s = cls.toUpperCase(Locale.US);
        if (s.startsWith("TOP SECRET")) return 0xFFFF8C00;
        if (s.startsWith("SECRET")) return 0xFFC8102E;
        if (s.startsWith("CONFIDENTIAL")) return 0xFF0033A0;
        if (s.startsWith("CUI") || s.contains("FOUO")) return 0xFF502B85;
        return 0xFF007A33;
    }

    // ----------------------------------------------------------- schematic

    private void drawSchematic(Canvas c, RectF r, float k) {
        Paint bg = new Paint();
        bg.setColor(0xFF22272E);
        c.drawRect(r, bg);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(20 * k);

        PngExportModel.Projection pr = PngExportModel.Projection.fit(plan, r.left, r.top, r.width(), r.height(), 60 * k);
        if (pr == null) {
            text.setTextSize(28 * k);
            c.drawText("No plan geometry to draw", r.left + 40 * k, r.centerY(), text);
            return;
        }
        c.save();
        c.clipRect(r);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3 * k);

        // Back to front: coverage rings, areas, corridors, emplacements.
        for (BasePlanItem item : plan.getAllItems()) {
            if (skip(item)) continue;
            if (!(item instanceof PointItem) || !item.isValid()) continue;
            PointItem p = (PointItem) item;
            float cx = pr.x(p.position), cy = pr.y(p.position), rad = pr.pixels(PngExportModel.coverageRadiusMeters(p));
            fill.setColor(PlanColors.withAlpha(p.color, 0.18f));
            stroke.setColor(p.color | 0xFF000000);
            if (p instanceof SensorItem && !((SensorItem) p).isOmnidirectional()) {
                SensorItem s = (SensorItem) p;
                RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);
                // Canvas angles: 0 = east, clockwise. Bearing: 0 = north, clockwise.
                float start = (float) (s.azimuthDeg - 90 - s.beamWidthDeg / 2);
                c.drawArc(oval, start, (float) s.beamWidthDeg, true, fill);
                c.drawArc(oval, start, (float) s.beamWidthDeg, true, stroke);
            } else {
                c.drawCircle(cx, cy, rad, fill);
                c.drawCircle(cx, cy, rad, stroke);
            }
        }
        for (BasePlanItem item : plan.getAllItems()) {
            if (skip(item)) continue;
            if (!(item instanceof PolygonItem) || !item.isValid()) continue;
            List<GeoPos> ring = PngExportModel.pointsOf(item);
            if (ring.size() < 3) continue;
            Path path = path(ring, pr, true);
            int fillColor = item instanceof DefendedAsset ? ((DefendedAsset) item).fillColor
                    : item instanceof EngagementAuthorityZone ? ((EngagementAuthorityZone) item).getFillArgb()
                    : PlanColors.withAlpha(item.color, 0.30f);
            fill.setColor(fillColor);
            stroke.setColor(item.color | 0xFF000000);
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
        }
        for (BasePlanItem item : plan.getAllItems()) {
            if (skip(item)) continue;
            if (!(item instanceof ThreatCorridor)) continue;
            ThreatCorridor t = (ThreatCorridor) item;
            if (t.points == null || t.points.size() < 2) continue;
            Paint corridor = new Paint(Paint.ANTI_ALIAS_FLAG);
            corridor.setStyle(Paint.Style.STROKE);
            corridor.setStrokeCap(Paint.Cap.ROUND);
            corridor.setStrokeJoin(Paint.Join.ROUND);
            corridor.setColor(PlanColors.withAlpha(t.color, 0.35f));
            corridor.setStrokeWidth(Math.max(4 * k, pr.pixels(t.widthMeters)));
            Path path = path(t.points, pr, false);
            c.drawPath(path, corridor);
            stroke.setColor(t.color | 0xFF000000);
            c.drawPath(path, stroke);
        }
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (BasePlanItem item : plan.getAllItems()) {
            if (skip(item)) continue;
            GeoPos a = item.getAnchor();
            if (a == null || !a.isValid()) continue;
            float x = pr.x(a), y = pr.y(a);
            if (item instanceof PointItem) {
                dot.setColor(item.color | 0xFF000000);
                c.drawCircle(x, y, 8 * k, dot);
            }
            text.setColor(Color.WHITE);
            c.drawText(item.label == null ? "" : item.label, x + 12 * k, y - 10 * k, text);
        }
        c.restore();
        drawNorthArrow(c, r.right - 70 * k, r.top + 30 * k, k);
        drawScaleBar(c, pr, r.left + 30 * k, r.bottom - 30 * k, r.width() / 4f, k);
    }

    private static Path path(List<GeoPos> pts, PngExportModel.Projection pr, boolean close) {
        Path p = new Path();
        for (int i = 0; i < pts.size(); i++) {
            GeoPos g = pts.get(i);
            if (i == 0) p.moveTo(pr.x(g), pr.y(g));
            else p.lineTo(pr.x(g), pr.y(g));
        }
        if (close) p.close();
        return p;
    }

    private static void drawNorthArrow(Canvas c, float x, float top, float k) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        Path a = new Path();
        a.moveTo(x, top);
        a.lineTo(x - 14 * k, top + 40 * k);
        a.lineTo(x, top + 30 * k);
        a.lineTo(x + 14 * k, top + 40 * k);
        a.close();
        c.drawPath(a, p);
        p.setTextSize(22 * k);
        p.setFakeBoldText(true);
        c.drawText("N", x - 8 * k, top + 66 * k, p);
    }

    private void drawScaleBar(Canvas c, PngExportModel.Projection pr, float x, float baseline, float maxPx, float k) {
        double meters = PngExportModel.niceScaleMeters(maxPx * pr.metersPerPixel);
        float px = pr.pixels(meters);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(4 * k);
        c.drawLine(x, baseline, x + px, baseline, p);
        c.drawLine(x, baseline - 10 * k, x, baseline + 2 * k, p);
        c.drawLine(x + px, baseline - 10 * k, x + px, baseline + 2 * k, p);
        p.setTextSize(20 * k);
        c.drawText(UnitConverter.format(meters, unit, decimals), x, baseline - 16 * k, p);
    }
}
