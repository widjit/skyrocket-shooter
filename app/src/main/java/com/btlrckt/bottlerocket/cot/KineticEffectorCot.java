package com.btlrckt.bottlerocket.cot;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.btlrckt.bottlerocket.model.AzimuthArc;
import com.btlrckt.bottlerocket.model.BasePlanItem;
import com.btlrckt.bottlerocket.model.DetonationMode;
import com.btlrckt.bottlerocket.model.EffectorStatus;
import com.btlrckt.bottlerocket.model.KineticEffector;
import com.btlrckt.bottlerocket.model.KineticEffectorType;
import com.btlrckt.bottlerocket.model.MunitionType;

import java.util.ArrayList;
import java.util.List;

/**
 * CoT encoding of a {@link KineticEffector}. Events use type {@link #COT_TYPE} and carry the
 * common BTLRCKT detail ({@code contact}, {@code remarks}, {@code __btlrckt}) plus a
 * {@code <btlrckt_kinetic>} element with the Phase 4 engagement-envelope fields:
 *
 * <pre>
 * &lt;btlrckt_kinetic type="ROCKET" minRange="50.0" azMin="10.0" azMax="120.0" elMin="-5.0" elMax="85.0"
 *     fragRouteWidth="10.0" munition="HE" detonation="PROXIMITY" restricted="30.0:45.0;200.0:210.0"
 *     minInterceptAlt="15.0" perimeterUid=".." ffCheck="true" sensors="uid1,uid2" designators=".."
 *     laserCode="1688" status="READY" ammo="12" reload="4.0" notes=".."
 *     dwell="3.0" apertureCm="30.0" powerKw="10.0" effRange="1500.0" suppressRadius="5.0" fluence="1000.0"/&gt;
 * </pre>
 * {@link #fromCot} also accepts the legacy {@link CotTypes#KINETIC_EFFECTOR} type.
 */
public final class KineticEffectorCot {
    public static final String COT_TYPE = CotTypes.PREFIX + "kinetic";
    public static final String DETAIL_ELEMENT = "btlrckt_kinetic";
    public static final int DEFAULT_STALE_DAYS = 30;

    private KineticEffectorCot() {
    }

    public static CotEvent toCot(KineticEffector e) {
        return toCot(e, "", DEFAULT_STALE_DAYS);
    }

    public static CotEvent toCot(KineticEffector e, String planUid, int staleDays) {
        CotEvent ev = CotSerializer.toCot(e, planUid, staleDays); // includes <btlrckt_kinetic>
        ev.setType(COT_TYPE);
        return ev;
    }

    /** @return the decoded effector, or null for non-kinetic / malformed events. */
    public static KineticEffector fromCot(CotEvent event) {
        if (event == null) return null;
        String type = event.getType();
        if (!COT_TYPE.equals(type) && !CotTypes.KINETIC_EFFECTOR.equals(type)) return null;
        BasePlanItem item = CotSerializer.fromCot(event);
        return item instanceof KineticEffector ? (KineticEffector) item : null;
    }

    /** Appends the {@code <btlrckt_kinetic>} element to {@code detail}. */
    public static void encodeInto(KineticEffector k, CotDetail detail) {
        CotDetail x = new CotDetail(DETAIL_ELEMENT);
        x.setAttribute("type", k.type == null ? KineticEffectorType.RIFLE.name() : k.type.name());
        x.setAttribute("minRange", d(k.minEngagementRange));
        if (k.azimuthMin != null) x.setAttribute("azMin", d(k.azimuthMin));
        if (k.azimuthMax != null) x.setAttribute("azMax", d(k.azimuthMax));
        x.setAttribute("elMin", d(k.elevationMin));
        x.setAttribute("elMax", d(k.elevationMax));
        x.setAttribute("fragRouteWidth", d(k.fragmentationRouteWidth));
        x.setAttribute("munition", k.munitionType == null ? "" : k.munitionType.name());
        x.setAttribute("detonation", k.detonationMode == null ? "" : k.detonationMode.name());
        x.setAttribute("restricted", encodeArcs(k.restrictedAzimuths));
        x.setAttribute("minInterceptAlt", d(k.minInterceptAltAGL));
        if (k.engagementAuthorityPerimeterUid != null) x.setAttribute("perimeterUid", k.engagementAuthorityPerimeterUid);
        x.setAttribute("ffCheck", String.valueOf(k.friendlyFireCheckEnabled));
        x.setAttribute("sensors", join(k.linkedSensorUids));
        x.setAttribute("designators", join(k.linkedLaserDesignatorUids));
        if (k.laserCode != null) x.setAttribute("laserCode", String.valueOf(k.laserCode));
        x.setAttribute("status", k.status == null ? "" : k.status.name());
        x.setAttribute("ammo", String.valueOf(k.ammoCount));
        x.setAttribute("reload", d(k.reloadTimeSec));
        x.setAttribute("notes", k.notes == null ? "" : k.notes);
        // R7 directed-energy fields (ignored by older builds).
        x.setAttribute("dwell", d(k.beamDwellSec));
        x.setAttribute("apertureCm", d(k.apertureDiameterCm));
        if (k.laserPowerKw != null) x.setAttribute("powerKw", d(k.laserPowerKw));
        if (k.qualitativeEffectiveRangeM != null) x.setAttribute("effRange", d(k.qualitativeEffectiveRangeM));
        x.setAttribute("suppressRadius", d(k.suppressionZoneRadiusM));
        x.setAttribute("fluence", d(k.damageFluenceJPerCm2));
        detail.addChild(x);
    }

    /** Reads the {@code <btlrckt_kinetic>} element (if present) of {@code detail} into {@code k}. */
    public static void decodeFrom(CotDetail detail, KineticEffector k) {
        if (detail == null) return;
        CotDetail x = CotSerializer.child(detail, DETAIL_ELEMENT);
        if (x == null) return;
        k.type = KineticEffectorType.fromName(x.getAttribute("type"), k.type);
        k.minEngagementRange = pd(x, "minRange", k.minEngagementRange);
        k.azimuthMin = boxed(x, "azMin");
        k.azimuthMax = boxed(x, "azMax");
        k.elevationMin = pd(x, "elMin", k.elevationMin);
        k.elevationMax = pd(x, "elMax", k.elevationMax);
        k.fragmentationRouteWidth = pd(x, "fragRouteWidth", k.fragmentationRouteWidth);
        k.munitionType = MunitionType.fromName(x.getAttribute("munition"), k.munitionType);
        k.detonationMode = DetonationMode.fromName(x.getAttribute("detonation"), k.detonationMode);
        k.restrictedAzimuths = decodeArcs(x.getAttribute("restricted"));
        k.minInterceptAltAGL = pd(x, "minInterceptAlt", k.minInterceptAltAGL);
        k.engagementAuthorityPerimeterUid = x.getAttribute("perimeterUid");
        String ff = x.getAttribute("ffCheck");
        if (ff != null) k.friendlyFireCheckEnabled = Boolean.parseBoolean(ff.trim());
        k.linkedSensorUids = split(x.getAttribute("sensors"));
        k.linkedLaserDesignatorUids = split(x.getAttribute("designators"));
        String lc = x.getAttribute("laserCode");
        k.laserCode = null;
        if (lc != null && !lc.trim().isEmpty()) {
            try {
                k.laserCode = Integer.parseInt(lc.trim());
            } catch (NumberFormatException ignored) {
                k.laserCode = null;
            }
        }
        k.status = EffectorStatus.fromName(x.getAttribute("status"), k.status);
        k.ammoCount = (int) pd(x, "ammo", k.ammoCount);
        k.reloadTimeSec = pd(x, "reload", k.reloadTimeSec);
        String notes = x.getAttribute("notes");
        k.notes = notes == null ? "" : notes;
        k.beamDwellSec = pd(x, "dwell", k.beamDwellSec);
        k.apertureDiameterCm = pd(x, "apertureCm", k.apertureDiameterCm);
        k.laserPowerKw = boxed(x, "powerKw");
        k.qualitativeEffectiveRangeM = boxed(x, "effRange");
        k.suppressionZoneRadiusM = pd(x, "suppressRadius", k.suppressionZoneRadiusM);
        k.damageFluenceJPerCm2 = pd(x, "fluence", k.damageFluenceJPerCm2);
        k.ensureLists();
    }

    // ------------------------------------------------------------------ helpers

    static String encodeArcs(List<AzimuthArc> arcs) {
        StringBuilder sb = new StringBuilder();
        if (arcs != null) {
            for (AzimuthArc a : arcs) {
                if (a == null) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append(a.encode());
            }
        }
        return sb.toString();
    }

    static List<AzimuthArc> decodeArcs(String s) {
        List<AzimuthArc> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split(";")) {
            AzimuthArc a = AzimuthArc.parse(part);
            if (a != null) out.add(a);
        }
        return out;
    }

    static String join(List<String> uids) {
        StringBuilder sb = new StringBuilder();
        if (uids != null) {
            for (String u : uids) {
                if (u == null || u.trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(u.trim());
            }
        }
        return sb.toString();
    }

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String d(double v) {
        return Double.toString(v);
    }

    private static Double boxed(CotDetail x, String attr) {
        String s = x.getAttribute(attr);
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double pd(CotDetail x, String attr, double dflt) {
        Double v = boxed(x, attr);
        return v == null ? dflt : v;
    }
}
