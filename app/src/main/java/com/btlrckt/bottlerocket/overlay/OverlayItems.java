package com.btlrckt.bottlerocket.overlay;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.btlrckt.bottlerocket.model.GeoPos;

import java.util.List;

/** Shared builders for the plugin's non-CoT display-only map items. */
final class OverlayItems {
    static final String META_ITEM_UID = "btlrckt_item_uid";

    private OverlayItems() {
    }

    static void tag(MapItem item, String ownerUid) {
        item.setMetaBoolean("nevercot", true);
        item.setMetaBoolean("addToObjList", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("removable", false);
        if (ownerUid != null) item.setMetaString(META_ITEM_UID, ownerUid);
    }

    static GeoPointMetaData[] toMetaPoints(List<GeoPos> pts) {
        GeoPointMetaData[] out = new GeoPointMetaData[pts.size()];
        for (int i = 0; i < out.length; i++) out[i] = GeoPointMetaData.wrap(pts.get(i).toGeoPoint());
        return out;
    }

    /** Closed polygon; {@code strokeArgb} alpha 0 or weight 0 = fill only. */
    static Polyline polygon(String uid, String ownerUid, List<GeoPos> pts, int fillArgb, int strokeArgb,
            double strokeWeight, boolean dashed) {
        Polyline p = new Polyline(uid);
        p.setType("u-d-f");
        p.setPoints(toMetaPoints(pts));
        boolean stroke = strokeWeight > 0 && (strokeArgb >>> 24) != 0;
        boolean fill = (fillArgb >>> 24) != 0;
        p.setStyle((stroke ? Shape.STYLE_STROKE_MASK : 0) | (fill ? Shape.STYLE_FILLED_MASK : 0)
                | Polyline.STYLE_CLOSED_MASK);
        p.setStrokeColor(strokeArgb);
        p.setStrokeWeight(stroke ? strokeWeight : 0d);
        p.setFillColor(fillArgb);
        p.setBasicLineStyle(dashed ? Polyline.BASIC_LINE_STYLE_DASHED : Polyline.BASIC_LINE_STYLE_SOLID);
        p.setClickable(false);
        tag(p, ownerUid);
        return p;
    }

    /** Point marker with a text label; {@code showIcon=false} gives a text-only callout. */
    static Marker label(String uid, String ownerUid, GeoPos at, String text, int textColor, boolean showIcon) {
        Marker m = new Marker(at.toGeoPoint(), uid);
        m.setType("b-m-p-s-p-i");
        m.setTitle(text);
        if (!showIcon) m.setIconVisibility(Marker.ICON_INVISIBLE);
        m.setTextColor(textColor | 0xFF000000);
        m.setClickable(false);
        tag(m, ownerUid);
        return m;
    }

    static int withAlpha(int argb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (argb & 0x00FFFFFF) | (a << 24);
    }
}
