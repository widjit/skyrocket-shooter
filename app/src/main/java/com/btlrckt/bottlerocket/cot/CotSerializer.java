package com.btlrckt.bottlerocket.cot;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.btlrckt.bottlerocket.model.BasePlanItem;
import com.btlrckt.bottlerocket.model.AuthorityType;
import com.btlrckt.bottlerocket.model.DefendedAsset;
import com.btlrckt.bottlerocket.model.EngagementAuthorityZone;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ItemKind;
import com.btlrckt.bottlerocket.model.KineticEffector;
import com.btlrckt.bottlerocket.model.NonKineticEffector;
import com.btlrckt.bottlerocket.model.PointDefensePlan;
import com.btlrckt.bottlerocket.model.PointItem;
import com.btlrckt.bottlerocket.model.RfBand;
import com.btlrckt.bottlerocket.model.RfHeatmapMode;
import com.btlrckt.bottlerocket.model.PolygonItem;
import com.btlrckt.bottlerocket.model.DayNight;
import com.btlrckt.bottlerocket.model.SensorItem;
import com.btlrckt.bottlerocket.model.SensorType;
import com.btlrckt.bottlerocket.model.ThreatCorridor;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts plan items to / from CoT events so they can be shared over TAK Server,
 * mesh networking and data packages (ATAK <-> WinTAK).
 *
 * <pre>
 * &lt;event uid="btlrckt-sensor-..." type="b-m-p-btlrckt-sensor" how="h-g-i-g-o"&gt;
 *   &lt;point lat=".." lon=".." hae="9999999" ce="9999999" le="9999999"/&gt;
 *   &lt;detail&gt;
 *     &lt;contact callsign="Radar 1"/&gt;
 *     &lt;remarks&gt;...&lt;/remarks&gt;
 *     &lt;__btlrckt v="1" kind="sensor" planUid=".." color=".." agl=".." .../&gt;
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

        CotDetail btlrckt = new CotDetail(CotTypes.DETAIL_BTLRCKT);
        btlrckt.setAttribute("v", String.valueOf(CotTypes.SCHEMA_VERSION));
        btlrckt.setAttribute("kind", item.getKind().code);
        btlrckt.setAttribute("planUid", nz(planUid));
        btlrckt.setAttribute("color", String.valueOf(item.color));
        btlrckt.setAttribute("created", String.valueOf(item.createdMillis));
        btlrckt.setAttribute("modified", String.valueOf(item.modifiedMillis));
        encodeSpecific(item, btlrckt, detail);
        detail.addChild(btlrckt);

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
        CotDetail btlrckt = new CotDetail(CotTypes.DETAIL_BTLRCKT);
        btlrckt.setAttribute("v", String.valueOf(CotTypes.SCHEMA_VERSION));
        btlrckt.setAttribute("kind", "plan");
        btlrckt.setAttribute("name", nz(plan.name));
        btlrckt.setAttribute("author", nz(plan.author));
        btlrckt.setAttribute("classification", nz(plan.classification));
        btlrckt.setAttribute("description", nz(plan.description));
        btlrckt.setAttribute("created", String.valueOf(plan.createdMillis));
        btlrckt.setAttribute("modified", String.valueOf(plan.modifiedMillis));
        btlrckt.setAttribute("itemCount", String.valueOf(plan.totalItems()));
        detail.addChild(btlrckt);
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

    /** Comma-joined uid list (uids never contain commas in TAK). */
    static String joinUids(java.util.List<String> uids) {
        StringBuilder sb = new StringBuilder();
        for (String u : uids) {
            if (u == null || u.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(u.trim());
        }
        return sb.toString();
    }

    static java.util.List<String> splitUids(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String u : s.split(",")) {
            String t = u.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    private static void encodeSpecific(BasePlanItem item, CotDetail btlrckt, CotDetail detail) {
        if (item instanceof PointItem) {
            PointItem p = (PointItem) item;
            btlrckt.setAttribute("agl", d(p.aglMeters));
            btlrckt.setAttribute("maxEng", d(p.maxEngagementDistMeters));
            if (p.position != null) btlrckt.setAttribute("pos", p.position.toCotString());
            if (p.hasSite()) btlrckt.setAttribute("siteUid", p.siteUid.trim());
        }
        if (item instanceof PolygonItem) {
            addLinks(detail, ((PolygonItem) item).points);
            addColor(detail, CotTypes.DETAIL_STROKE_COLOR, item.color);
        }
        switch (item.getKind()) {
            case EXCLUSION_ZONE:
                ExclusionZoneCotSerializer.encode((ExclusionZone) item, btlrckt, detail);
                break;
            case DEFENDED_ASSET: {
                DefendedAsset a = (DefendedAsset) item;
                btlrckt.setAttribute("fillColor", String.valueOf(a.fillColor));
                btlrckt.setAttribute("borderColor", String.valueOf(a.borderColor));
                btlrckt.setAttribute("priority", String.valueOf(a.priority));
                addColor(detail, CotTypes.DETAIL_FILL_COLOR, a.fillColor);
                break;
            }
            case SENSOR: {
                SensorItem s = (SensorItem) item;
                btlrckt.setAttribute("sensorType", s.type.name());
                btlrckt.setAttribute("range", d(s.rangeMeters));
                btlrckt.setAttribute("beamWidth", d(s.beamWidthDeg));
                btlrckt.setAttribute("azimuth", d(s.azimuthDeg));
                btlrckt.setAttribute("opacity", Float.toString(s.opacity));
                if (s.linkedSensorUids != null && !s.linkedSensorUids.isEmpty()) {
                    btlrckt.setAttribute("linkedSensors", joinUids(s.linkedSensorUids));
                }
                // R2 (optional attributes; absent on older peers -> defaults).
                if (s.minRangeMeters > 0) btlrckt.setAttribute("minRange", d(s.minRangeMeters));
                if (!Double.isNaN(s.elevationMinDeg)) btlrckt.setAttribute("elevMin", d(s.elevationMinDeg));
                if (!Double.isNaN(s.elevationMaxDeg)) btlrckt.setAttribute("elevMax", d(s.elevationMaxDeg));
                s.ensureLists();
                btlrckt.setAttribute("dayNight", s.dayNight.name());
                if (s.type == SensorType.LASER_DESIGNATOR) {
                    btlrckt.setAttribute("maxLasingRange", d(s.maxLasingRangeMeters));
                    btlrckt.setAttribute("laserPrf", String.valueOf(s.laserPrfCode));
                    btlrckt.setAttribute("beamDivergence", d(s.beamDivergenceMrad));
                }
                break;
            }
            case NON_KINETIC_EFFECTOR: {
                NonKineticEffector n = (NonKineticEffector) item;
                btlrckt.setAttribute("rfThresholdDbm", d(n.rfThresholdDbm));
                btlrckt.setAttribute("heatmapColor", String.valueOf(n.heatmapColor));
                btlrckt.setAttribute("transparency", String.valueOf(n.transparency));
                btlrckt.setAttribute("freqMhz", d(n.frequencyMhz));
                btlrckt.setAttribute("eirpDbm", d(n.eirpDbm));
                btlrckt.setAttribute("useGlobalThreshold", String.valueOf(n.useGlobalThreshold));
                btlrckt.setAttribute("useGlobalFreq", String.valueOf(n.useGlobalFrequency));
                btlrckt.setAttribute("marginalColor", String.valueOf(n.marginalColor));
                btlrckt.setAttribute("insufficientColor", String.valueOf(n.insufficientColor));
                if (n.linkedSensorUids != null && !n.linkedSensorUids.isEmpty()) {
                    btlrckt.setAttribute("linkedSensors", joinUids(n.linkedSensorUids));
                }
                if (n.isMultiBand()) {
                    btlrckt.setAttribute("bands", RfBand.encodeList(n.bands));
                    btlrckt.setAttribute("heatmapMode", n.getHeatmapMode().name());
                }
                break;
            }
            case KINETIC_EFFECTOR: {
                KineticEffector k = (KineticEffector) item;
                btlrckt.setAttribute("fragRadius", d(k.fragmentationRadiusMeters));
                btlrckt.setAttribute("munitionSpeed", d(k.munitionSpeedMps));
                btlrckt.setAttribute("reactionTime", d(k.reactionTimeSec));
                btlrckt.setAttribute("noShootColor", String.valueOf(k.noShootColor));
                btlrckt.setAttribute("fragZoneColor", String.valueOf(k.fragZoneColor));
                btlrckt.setAttribute("clearZoneColor", String.valueOf(k.clearZoneColor));
                KineticEffectorCot.encodeInto(k, detail);
                break;
            }
            case AUTHORITY_ZONE: {
                EngagementAuthorityZone z = (EngagementAuthorityZone) item;
                btlrckt.setAttribute("authorityType", z.authorityType.name());
                btlrckt.setAttribute("bufferM", d(z.bufferMeters));
                if (z.baseUid != null) btlrckt.setAttribute("baseUid", z.baseUid);
                btlrckt.setAttribute("fillColor", String.valueOf(z.fillColor));
                btlrckt.setAttribute("fillOpacity", Float.toString(z.fillOpacity));
                btlrckt.setAttribute("dashed", String.valueOf(z.strokeDashed));
                addColor(detail, CotTypes.DETAIL_FILL_COLOR, z.getFillArgb());
                break;
            }
            case THREAT_CORRIDOR: {
                ThreatCorridor c = (ThreatCorridor) item;
                btlrckt.setAttribute("width", d(c.widthMeters));
                btlrckt.setAttribute("threatSpeed", d(c.threatSpeedMps));
                btlrckt.setAttribute("threatAltAgl", d(c.threatAltitudeAglMeters));
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

    /** @return the BTLRCKT item kind of an event, or null if it is not a BTLRCKT item. */
    public static ItemKind kindOf(CotEvent ev) {
        return ev == null ? null : CotTypes.kindForType(ev.getType());
    }

    /** Decodes a BTLRCKT item event. Returns null for foreign / malformed events. */
    public static BasePlanItem fromCot(CotEvent ev) {
        ItemKind kind = kindOf(ev);
        if (kind == null || ev.getDetail() == null) return null;
        CotDetail btlrckt = child(ev.getDetail(), CotTypes.DETAIL_BTLRCKT);
        if (btlrckt == null) return null;

        BasePlanItem item;
        switch (kind) {
            case EXCLUSION_ZONE: {
                ExclusionZone z = new ExclusionZone();
                ExclusionZoneCotSerializer.decode(btlrckt, ev.getDetail(), z);
                item = z;
                break;
            }
            case DEFENDED_ASSET: {
                DefendedAsset a = new DefendedAsset();
                a.fillColor = pi(btlrckt, "fillColor", a.fillColor);
                a.borderColor = pi(btlrckt, "borderColor", a.borderColor);
                a.priority = pi(btlrckt, "priority", a.priority);
                item = a;
                break;
            }
            case SENSOR: {
                SensorItem s = new SensorItem();
                s.type = SensorType.fromName(btlrckt.getAttribute("sensorType"), SensorType.RADAR);
                s.rangeMeters = pd(btlrckt, "range", s.rangeMeters);
                s.beamWidthDeg = pd(btlrckt, "beamWidth", s.beamWidthDeg);
                s.azimuthDeg = pd(btlrckt, "azimuth", s.azimuthDeg);
                s.opacity = (float) Math.max(0, Math.min(1, pd(btlrckt, "opacity", s.opacity)));
                s.linkedSensorUids = splitUids(btlrckt.getAttribute("linkedSensors"));
                s.minRangeMeters = Math.max(0, pd(btlrckt, "minRange", s.minRangeMeters));
                s.elevationMinDeg = pd(btlrckt, "elevMin", s.elevationMinDeg);
                s.elevationMaxDeg = pd(btlrckt, "elevMax", s.elevationMaxDeg);
                s.dayNight = DayNight.fromName(btlrckt.getAttribute("dayNight"), DayNight.DAY_NIGHT);
                s.maxLasingRangeMeters = Math.max(0, pd(btlrckt, "maxLasingRange", s.maxLasingRangeMeters));
                s.laserPrfCode = pi(btlrckt, "laserPrf", s.laserPrfCode);
                s.beamDivergenceMrad = Math.max(0, pd(btlrckt, "beamDivergence", s.beamDivergenceMrad));
                item = s;
                break;
            }
            case NON_KINETIC_EFFECTOR: {
                NonKineticEffector n = new NonKineticEffector();
                n.rfThresholdDbm = pd(btlrckt, "rfThresholdDbm", n.rfThresholdDbm);
                n.heatmapColor = pi(btlrckt, "heatmapColor", n.heatmapColor);
                n.transparency = (float) pd(btlrckt, "transparency", n.transparency);
                n.frequencyMhz = pd(btlrckt, "freqMhz", n.frequencyMhz);
                n.eirpDbm = pd(btlrckt, "eirpDbm", n.eirpDbm);
                // Legacy (schema-1, pre Phase 3) events carry only a per-effector threshold /
                // frequency: honour them as overrides when the flags are absent.
                n.useGlobalThreshold = pb(btlrckt, "useGlobalThreshold", btlrckt.getAttribute("rfThresholdDbm") == null);
                n.useGlobalFrequency = pb(btlrckt, "useGlobalFreq", btlrckt.getAttribute("freqMhz") == null);
                n.marginalColor = pi(btlrckt, "marginalColor", n.marginalColor);
                n.insufficientColor = pi(btlrckt, "insufficientColor", n.insufficientColor);
                n.linkedSensorUids = splitUids(btlrckt.getAttribute("linkedSensors"));
                n.bands = RfBand.decodeList(btlrckt.getAttribute("bands"));
                n.heatmapMode = RfHeatmapMode.parse(btlrckt.getAttribute("heatmapMode"));
                item = n;
                break;
            }
            case KINETIC_EFFECTOR: {
                KineticEffector k = new KineticEffector();
                k.fragmentationRadiusMeters = pd(btlrckt, "fragRadius", k.fragmentationRadiusMeters);
                k.munitionSpeedMps = pd(btlrckt, "munitionSpeed", k.munitionSpeedMps);
                k.reactionTimeSec = pd(btlrckt, "reactionTime", k.reactionTimeSec);
                k.noShootColor = pi(btlrckt, "noShootColor", k.noShootColor);
                k.fragZoneColor = pi(btlrckt, "fragZoneColor", k.fragZoneColor);
                k.clearZoneColor = pi(btlrckt, "clearZoneColor", k.clearZoneColor);
                KineticEffectorCot.decodeFrom(ev.getDetail(), k);
                item = k;
                break;
            }
            case AUTHORITY_ZONE: {
                EngagementAuthorityZone z = new EngagementAuthorityZone();
                z.authorityType = AuthorityType.fromName(btlrckt.getAttribute("authorityType"), AuthorityType.KINETIC);
                z.bufferMeters = pd(btlrckt, "bufferM", 0.0);
                z.baseUid = btlrckt.getAttribute("baseUid");
                z.fillColor = pi(btlrckt, "fillColor", z.authorityType.defaultColor);
                z.fillOpacity = (float) pd(btlrckt, "fillOpacity", z.fillOpacity);
                z.strokeDashed = pb(btlrckt, "dashed", z.strokeDashed);
                z.normalize();
                item = z;
                break;
            }
            case THREAT_CORRIDOR: {
                ThreatCorridor c = new ThreatCorridor();
                c.widthMeters = pd(btlrckt, "width", c.widthMeters);
                c.threatSpeedMps = pd(btlrckt, "threatSpeed", c.threatSpeedMps);
                c.threatAltitudeAglMeters = pd(btlrckt, "threatAltAgl", c.threatAltitudeAglMeters);
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
        item.color = pi(btlrckt, "color", item.color);
        item.createdMillis = pl(btlrckt, "created", item.createdMillis);
        item.modifiedMillis = pl(btlrckt, "modified", item.modifiedMillis);

        if (item instanceof PolygonItem) {
            ((PolygonItem) item).points = readLinks(ev.getDetail());
        }
        if (item instanceof PointItem) {
            PointItem p = (PointItem) item;
            p.aglMeters = pd(btlrckt, "agl", p.aglMeters);
            p.maxEngagementDistMeters = pd(btlrckt, "maxEng", p.maxEngagementDistMeters);
            GeoPos pos = GeoPos.fromCotString(btlrckt.getAttribute("pos"));
            if (pos == null && ev.getCotPoint() != null) {
                double hae = ev.getCotPoint().getHae();
                pos = new GeoPos(ev.getCotPoint().getLat(), ev.getCotPoint().getLon(),
                        hae >= CotTypes.COT_UNKNOWN ? Double.NaN : hae);
            }
            p.position = pos != null ? pos : new GeoPos();
            p.siteUid = PointItem.normalizeSiteUid(btlrckt.getAttribute("siteUid"));
        }
        return item;
    }

    /** Planning-group UID an item event belongs to (may be empty). */
    public static String planUidOf(CotEvent ev) {
        CotDetail btlrckt = ev == null || ev.getDetail() == null ? null : child(ev.getDetail(), CotTypes.DETAIL_BTLRCKT);
        return btlrckt == null ? null : btlrckt.getAttribute("planUid");
    }

    /** Decodes plan metadata from a {@link CotTypes#PLAN} event into a new, empty plan. */
    public static PointDefensePlan planFromCot(CotEvent ev) {
        if (ev == null || !CotTypes.PLAN.equals(ev.getType()) || ev.getDetail() == null) return null;
        CotDetail btlrckt = child(ev.getDetail(), CotTypes.DETAIL_BTLRCKT);
        if (btlrckt == null) return null;
        PointDefensePlan plan = new PointDefensePlan();
        plan.uid = ev.getUID();
        plan.name = valueOr(btlrckt.getAttribute("name"), plan.name);
        plan.author = valueOr(btlrckt.getAttribute("author"), "");
        plan.classification = valueOr(btlrckt.getAttribute("classification"), PointDefensePlan.DEFAULT_CLASSIFICATION);
        plan.description = valueOr(btlrckt.getAttribute("description"), "");
        plan.createdMillis = pl(btlrckt, "created", plan.createdMillis);
        plan.modifiedMillis = pl(btlrckt, "modified", plan.modifiedMillis);
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
