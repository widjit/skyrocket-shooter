package com.skiarkt.shooter.cot;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.DefendedAsset;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.model.PointItem;
import com.skiarkt.shooter.model.PolygonItem;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.model.SensorType;
import com.skiarkt.shooter.model.ThreatCorridor;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts plan items to / from CoT events so they can be shared over TAK Server,
 * mesh networking and data packages (ATAK <-> WinTAK).
 *
 * <pre>
 * &lt;event uid="skiarkt-sensor-..." type="b-m-p-skiarkt-sensor" how="h-g-i-g-o"&gt;
 *   &lt;point lat=".." lon=".." hae="9999999" ce="9999999" le="9999999"/&gt;
 *   &lt;detail&gt;
 *     &lt;contact callsign="Radar 1"/&gt;
 *     &lt;remarks&gt;...&lt;/remarks&gt;
 *     &lt;__skiarkt v="1" kind="sensor" planUid=".." color=".." agl=".." .../&gt;
 *     &lt;link point="lat,lon,hae"/&gt;   (polygon / polyline vertices)
 *   &lt;/detail&gt;
 * &lt;/event&gt;
 * </pre>
 * All numbers are encoded with {@link Double#toString} (locale independent).
 */
public final class CotSerializer {
    private CotSerializer() {
    }

    // ------------------------------------------------------------------ encode

    public static CotEvent toCot(BasePlanItem item, String planUid, int staleDays) {
        CotEvent ev = baseEvent(item.uid, CotTypes.forKind(item.getKind()), item.getAnchor(), staleDays);
        CotDetail detail = new CotDetail("detail");

        CotDetail contact = new CotDetail(CotTypes.DETAIL_CONTACT);
        contact.setAttribute("callsign", nz(item.label));
        detail.addChild(contact);

        CotDetail remarks = new CotDetail(CotTypes.DETAIL_REMARKS);
        remarks.setInnerText(nz(item.remarks));
        detail.addChild(remarks);

        CotDetail skiarkt = new CotDetail(CotTypes.DETAIL_SKIARKT);
        skiarkt.setAttribute("v", String.valueOf(CotTypes.SCHEMA_VERSION));
        skiarkt.setAttribute("kind", item.getKind().code);
        skiarkt.setAttribute("planUid", nz(planUid));
        skiarkt.setAttribute("color", String.valueOf(item.color));
        skiarkt.setAttribute("created", String.valueOf(item.createdMillis));
        skiarkt.setAttribute("modified", String.valueOf(item.modifiedMillis));
        encodeSpecific(item, skiarkt, detail);
        detail.addChild(skiarkt);

        ev.setDetail(detail);
        return ev;
    }

    public static CotEvent planToCot(PointDefensePlan plan, int staleDays) {
        GeoPos anchor = null;
        for (BasePlanItem i : plan.getAllItems()) {
            if (i.getAnchor() != null) {
                anchor = i.getAnchor();
                break;
            }
        }
        CotEvent ev = baseEvent(plan.uid, CotTypes.PLAN, anchor, staleDays);
        CotDetail detail = new CotDetail("detail");
        CotDetail contact = new CotDetail(CotTypes.DETAIL_CONTACT);
        contact.setAttribute("callsign", nz(plan.name));
        detail.addChild(contact);
        CotDetail skiarkt = new CotDetail(CotTypes.DETAIL_SKIARKT);
        skiarkt.setAttribute("v", String.valueOf(CotTypes.SCHEMA_VERSION));
        skiarkt.setAttribute("kind", "plan");
        skiarkt.setAttribute("name", nz(plan.name));
        skiarkt.setAttribute("author", nz(plan.author));
        skiarkt.setAttribute("classification", nz(plan.classification));
        skiarkt.setAttribute("description", nz(plan.description));
        skiarkt.setAttribute("created", String.valueOf(plan.createdMillis));
        skiarkt.setAttribute("modified", String.valueOf(plan.modifiedMillis));
        skiarkt.setAttribute("itemCount", String.valueOf(plan.totalItems()));
        detail.addChild(skiarkt);
        ev.setDetail(detail);
        return ev;
    }

    /** Full plan as an ordered list: plan header first, then every item. */
    public static List<CotEvent> planToCotEvents(PointDefensePlan plan, int staleDays) {
        List<CotEvent> out = new ArrayList<>();
        out.add(planToCot(plan, staleDays));
        for (BasePlanItem i : plan.getAllItems()) {
            out.add(toCot(i, plan.uid, staleDays));
        }
        return out;
    }

    private static CotEvent baseEvent(String uid, String type, GeoPos anchor, int staleDays) {
        CotEvent ev = new CotEvent();
        CoordinatedTime now = new CoordinatedTime();
        ev.setUID(uid);
        ev.setType(type);
        ev.setVersion("2.0");
        ev.setHow(CotTypes.HOW);
        ev.setTime(now);
        ev.setStart(now);
        ev.setStale(now.addDays(Math.max(1, staleDays)));
        double lat = anchor != null ? anchor.lat : 0.0;
        double lon = anchor != null ? anchor.lon : 0.0;
        double hae = anchor != null && anchor.hasAltitude() ? anchor.altHae : CotTypes.COT_UNKNOWN;
        ev.setPoint(new CotPoint(lat, lon, hae, CotTypes.COT_UNKNOWN, CotTypes.COT_UNKNOWN));
        return ev;
    }

    private static void encodeSpecific(BasePlanItem item, CotDetail skiarkt, CotDetail detail) {
        if (item instanceof PointItem) {
            PointItem p = (PointItem) item;
            skiarkt.setAttribute("agl", d(p.aglMeters));
            skiarkt.setAttribute("maxEng", d(p.maxEngagementDistMeters));
            if (p.position != null) skiarkt.setAttribute("pos", p.position.toCotString());
        }
        if (item instanceof PolygonItem) {
            addLinks(detail, ((PolygonItem) item).points);
            addColor(detail, CotTypes.DETAIL_STROKE_COLOR, item.color);
        }
        switch (item.getKind()) {
            case EXCLUSION_ZONE:
                ExclusionZoneCotSerializer.encode((ExclusionZone) item, skiarkt, detail);
                break;
            case DEFENDED_ASSET: {
                DefendedAsset a = (DefendedAsset) item;
                skiarkt.setAttribute("fillColor", String.valueOf(a.fillColor));
                skiarkt.setAttribute("borderColor", String.valueOf(a.borderColor));
                skiarkt.setAttribute("priority", String.valueOf(a.priority));
                addColor(detail, CotTypes.DETAIL_FILL_COLOR, a.fillColor);
                break;
            }
            case SENSOR: {
                SensorItem s = (SensorItem) item;
                skiarkt.setAttribute("sensorType", s.type.name());
                skiarkt.setAttribute("range", d(s.rangeMeters));
                skiarkt.setAttribute("beamWidth", d(s.beamWidthDeg));
                skiarkt.setAttribute("azimuth", d(s.azimuthDeg));
                skiarkt.setAttribute("opacity", Float.toString(s.opacity));
                break;
            }
            case NON_KINETIC_EFFECTOR: {
                NonKineticEffector n = (NonKineticEffector) item;
                skiarkt.setAttribute("rfThresholdDbm", d(n.rfThresholdDbm));
                skiarkt.setAttribute("heatmapColor", String.valueOf(n.heatmapColor));
                skiarkt.setAttribute("transparency", String.valueOf(n.transparency));
                skiarkt.setAttribute("freqMhz", d(n.frequencyMhz));
                skiarkt.setAttribute("eirpDbm", d(n.eirpDbm));
                skiarkt.setAttribute("useGlobalThreshold", String.valueOf(n.useGlobalThreshold));
                skiarkt.setAttribute("useGlobalFreq", String.valueOf(n.useGlobalFrequency));
                skiarkt.setAttribute("marginalColor", String.valueOf(n.marginalColor));
                skiarkt.setAttribute("insufficientColor", String.valueOf(n.insufficientColor));
                break;
            }
            case KINETIC_EFFECTOR: {
                KineticEffector k = (KineticEffector) item;
                skiarkt.setAttribute("fragRadius", d(k.fragmentationRadiusMeters));
                skiarkt.setAttribute("munitionSpeed", d(k.munitionSpeedMps));
                skiarkt.setAttribute("reactionTime", d(k.reactionTimeSec));
                skiarkt.setAttribute("noShootColor", String.valueOf(k.noShootColor));
                skiarkt.setAttribute("fragZoneColor", String.valueOf(k.fragZoneColor));
                skiarkt.setAttribute("clearZoneColor", String.valueOf(k.clearZoneColor));
                KineticEffectorCot.encodeInto(k, detail);
                break;
            }
            case THREAT_CORRIDOR: {
                ThreatCorridor c = (ThreatCorridor) item;
                skiarkt.setAttribute("width", d(c.widthMeters));
                skiarkt.setAttribute("threatSpeed", d(c.threatSpeedMps));
                skiarkt.setAttribute("threatAltAgl", d(c.threatAltitudeAglMeters));
                addLinks(detail, c.points);
                addColor(detail, CotTypes.DETAIL_STROKE_COLOR, c.color);
                break;
            }
            default:
                break;
        }
    }

    private static void addLinks(CotDetail detail, List<GeoPos> pts) {
        if (pts == null) return;
        for (GeoPos p : pts) {
            CotDetail link = new CotDetail(CotTypes.DETAIL_LINK);
            link.setAttribute("point", p.toCotString());
            detail.addChild(link);
        }
    }

    private static void addColor(CotDetail detail, String element, int argb) {
        CotDetail c = new CotDetail(element);
        c.setAttribute("value", String.valueOf(argb));
        detail.addChild(c);
    }

    // ------------------------------------------------------------------ decode

    /** @return the SKIARKT item kind of an event, or null if it is not a SKIARKT item. */
    public static ItemKind kindOf(CotEvent ev) {
        return ev == null ? null : CotTypes.kindForType(ev.getType());
    }

    /** Decodes a SKIARKT item event. Returns null for foreign / malformed events. */
    public static BasePlanItem fromCot(CotEvent ev) {
        ItemKind kind = kindOf(ev);
        if (kind == null || ev.getDetail() == null) return null;
        CotDetail skiarkt = child(ev.getDetail(), CotTypes.DETAIL_SKIARKT);
        if (skiarkt == null) return null;

        BasePlanItem item;
        switch (kind) {
            case EXCLUSION_ZONE: {
                ExclusionZone z = new ExclusionZone();
                ExclusionZoneCotSerializer.decode(skiarkt, ev.getDetail(), z);
                item = z;
                break;
            }
            case DEFENDED_ASSET: {
                DefendedAsset a = new DefendedAsset();
                a.fillColor = pi(skiarkt, "fillColor", a.fillColor);
                a.borderColor = pi(skiarkt, "borderColor", a.borderColor);
                a.priority = pi(skiarkt, "priority", a.priority);
                item = a;
                break;
            }
            case SENSOR: {
                SensorItem s = new SensorItem();
                s.type = SensorType.fromName(skiarkt.getAttribute("sensorType"), SensorType.RADAR);
                s.rangeMeters = pd(skiarkt, "range", s.rangeMeters);
                s.beamWidthDeg = pd(skiarkt, "beamWidth", s.beamWidthDeg);
                s.azimuthDeg = pd(skiarkt, "azimuth", s.azimuthDeg);
                s.opacity = (float) Math.max(0, Math.min(1, pd(skiarkt, "opacity", s.opacity)));
                item = s;
                break;
            }
            case NON_KINETIC_EFFECTOR: {
                NonKineticEffector n = new NonKineticEffector();
                n.rfThresholdDbm = pd(skiarkt, "rfThresholdDbm", n.rfThresholdDbm);
                n.heatmapColor = pi(skiarkt, "heatmapColor", n.heatmapColor);
                n.transparency = (float) pd(skiarkt, "transparency", n.transparency);
                n.frequencyMhz = pd(skiarkt, "freqMhz", n.frequencyMhz);
                n.eirpDbm = pd(skiarkt, "eirpDbm", n.eirpDbm);
                // Legacy (schema-1, pre Phase 3) events carry only a per-effector threshold /
                // frequency: honour them as overrides when the flags are absent.
                n.useGlobalThreshold = pb(skiarkt, "useGlobalThreshold", skiarkt.getAttribute("rfThresholdDbm") == null);
                n.useGlobalFrequency = pb(skiarkt, "useGlobalFreq", skiarkt.getAttribute("freqMhz") == null);
                n.marginalColor = pi(skiarkt, "marginalColor", n.marginalColor);
                n.insufficientColor = pi(skiarkt, "insufficientColor", n.insufficientColor);
                item = n;
                break;
            }
            case KINETIC_EFFECTOR: {
                KineticEffector k = new KineticEffector();
                k.fragmentationRadiusMeters = pd(skiarkt, "fragRadius", k.fragmentationRadiusMeters);
                k.munitionSpeedMps = pd(skiarkt, "munitionSpeed", k.munitionSpeedMps);
                k.reactionTimeSec = pd(skiarkt, "reactionTime", k.reactionTimeSec);
                k.noShootColor = pi(skiarkt, "noShootColor", k.noShootColor);
                k.fragZoneColor = pi(skiarkt, "fragZoneColor", k.fragZoneColor);
                k.clearZoneColor = pi(skiarkt, "clearZoneColor", k.clearZoneColor);
                KineticEffectorCot.decodeFrom(ev.getDetail(), k);
                item = k;
                break;
            }
            case THREAT_CORRIDOR: {
                ThreatCorridor c = new ThreatCorridor();
                c.widthMeters = pd(skiarkt, "width", c.widthMeters);
                c.threatSpeedMps = pd(skiarkt, "threatSpeed", c.threatSpeedMps);
                c.threatAltitudeAglMeters = pd(skiarkt, "threatAltAgl", c.threatAltitudeAglMeters);
                c.points = readLinks(ev.getDetail());
                item = c;
                break;
            }
            default:
                return null;
        }

        item.uid = ev.getUID();
        CotDetail contact = child(ev.getDetail(), CotTypes.DETAIL_CONTACT);
        if (contact != null && contact.getAttribute("callsign") != null) {
            item.label = contact.getAttribute("callsign");
        }
        CotDetail remarks = child(ev.getDetail(), CotTypes.DETAIL_REMARKS);
        item.remarks = remarks != null && remarks.getInnerText() != null ? remarks.getInnerText() : "";
        item.color = pi(skiarkt, "color", item.color);
        item.createdMillis = pl(skiarkt, "created", item.createdMillis);
        item.modifiedMillis = pl(skiarkt, "modified", item.modifiedMillis);

        if (item instanceof PolygonItem) {
            ((PolygonItem) item).points = readLinks(ev.getDetail());
        }
        if (item instanceof PointItem) {
            PointItem p = (PointItem) item;
            p.aglMeters = pd(skiarkt, "agl", p.aglMeters);
            p.maxEngagementDistMeters = pd(skiarkt, "maxEng", p.maxEngagementDistMeters);
            GeoPos pos = GeoPos.fromCotString(skiarkt.getAttribute("pos"));
            if (pos == null && ev.getCotPoint() != null) {
                double hae = ev.getCotPoint().getHae();
                pos = new GeoPos(ev.getCotPoint().getLat(), ev.getCotPoint().getLon(),
                        hae >= CotTypes.COT_UNKNOWN ? Double.NaN : hae);
            }
            p.position = pos != null ? pos : new GeoPos();
        }
        return item;
    }

    /** Planning-group UID an item event belongs to (may be empty). */
    public static String planUidOf(CotEvent ev) {
        CotDetail skiarkt = ev == null || ev.getDetail() == null ? null : child(ev.getDetail(), CotTypes.DETAIL_SKIARKT);
        return skiarkt == null ? null : skiarkt.getAttribute("planUid");
    }

    /** Decodes plan metadata from a {@link CotTypes#PLAN} event into a new, empty plan. */
    public static PointDefensePlan planFromCot(CotEvent ev) {
        if (ev == null || !CotTypes.PLAN.equals(ev.getType()) || ev.getDetail() == null) return null;
        CotDetail skiarkt = child(ev.getDetail(), CotTypes.DETAIL_SKIARKT);
        if (skiarkt == null) return null;
        PointDefensePlan plan = new PointDefensePlan();
        plan.uid = ev.getUID();
        plan.name = valueOr(skiarkt.getAttribute("name"), plan.name);
        plan.author = valueOr(skiarkt.getAttribute("author"), "");
        plan.classification = valueOr(skiarkt.getAttribute("classification"), PointDefensePlan.DEFAULT_CLASSIFICATION);
        plan.description = valueOr(skiarkt.getAttribute("description"), "");
        plan.createdMillis = pl(skiarkt, "created", plan.createdMillis);
        plan.modifiedMillis = pl(skiarkt, "modified", plan.modifiedMillis);
        return plan;
    }

    private static List<GeoPos> readLinks(CotDetail detail) {
        List<GeoPos> pts = new ArrayList<>();
        for (CotDetail c : detail.getChildren()) {
            if (CotTypes.DETAIL_LINK.equals(c.getElementName())) {
                GeoPos p = GeoPos.fromCotString(c.getAttribute("point"));
                if (p != null) pts.add(p);
            }
        }
        return pts;
    }

    static CotDetail child(CotDetail parent, String name) {
        for (CotDetail c : parent.getChildren()) {
            if (name.equals(c.getElementName())) return c;
        }
        return null;
    }

    private static String d(double v) {
        return Double.toString(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String valueOr(String s, String dflt) {
        return s == null || s.isEmpty() ? dflt : s;
    }

    private static double pd(CotDetail d, String attr, double dflt) {
        String s = d.getAttribute(attr);
        if (s == null) return dflt;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static int pi(CotDetail d, String attr, int dflt) {
        String s = d.getAttribute(attr);
        if (s == null) return dflt;
        try {
            return (int) Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static boolean pb(CotDetail d, String attr, boolean dflt) {
        String s = d.getAttribute(attr);
        return s == null ? dflt : Boolean.parseBoolean(s.trim());
    }

    private static long pl(CotDetail d, String attr, long dflt) {
        String s = d.getAttribute(attr);
        if (s == null) return dflt;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
