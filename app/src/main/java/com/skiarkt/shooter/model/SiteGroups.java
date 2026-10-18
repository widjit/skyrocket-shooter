package com.skiarkt.shooter.model;

import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * R0 site groups: point items (sensors, kinetic and non-kinetic effectors) that share a
 * {@link PointItem#siteUid}. The site uid doubles as the human-readable site name
 * (e.g. "Alpha-1"). Pure model helper (no Android), used by the panels, overlay and tests.
 */
public final class SiteGroups {
    /** Minimum radius (m) of the site ring drawn around co-located members. */
    public static final double MIN_RING_RADIUS_M = 25.0;
    /** Padding (m) added outside the furthest member. */
    public static final double RING_PADDING_M = 15.0;

    /** One site and its members. */
    public static final class Site {
        public final String uid;
        public final List<PointItem> members = new ArrayList<>();

        Site(String uid) {
            this.uid = uid;
        }

        public int count(ItemKind kind) {
            int n = 0;
            for (PointItem p : members) if (p.getKind() == kind) n++;
            return n;
        }

        public List<String> memberUids() {
            List<String> out = new ArrayList<>();
            for (PointItem p : members) out.add(p.uid);
            return out;
        }

        /** Mean position of the members with valid positions (null when none). */
        public GeoPos center() {
            double lat = 0, lon = 0;
            int n = 0;
            for (PointItem p : members) {
                if (p.position == null || !p.position.isValid()) continue;
                lat += p.position.lat;
                lon += p.position.lon;
                n++;
            }
            return n == 0 ? null : new GeoPos(lat / n, lon / n);
        }

        /** Ring radius (m): furthest member from the center + padding, never below the minimum. */
        public double ringRadiusMeters() {
            GeoPos c = center();
            double r = 0;
            if (c != null) {
                for (PointItem p : members) {
                    if (p.position == null || !p.position.isValid()) continue;
                    r = Math.max(r, GeoUtils.distanceMeters(c, p.position));
                }
            }
            return Math.max(MIN_RING_RADIUS_M, r + RING_PADDING_M);
        }

        /** "Alpha-1: 3 sensors, 1 kinetic, 1 NK". */
        public String summary() {
            StringBuilder sb = new StringBuilder(uid).append(": ");
            int s = count(ItemKind.SENSOR), k = count(ItemKind.KINETIC_EFFECTOR), n = count(ItemKind.NON_KINETIC_EFFECTOR);
            List<String> parts = new ArrayList<>();
            if (s > 0) parts.add(s + (s == 1 ? " sensor" : " sensors"));
            if (k > 0) parts.add(k + " kinetic");
            if (n > 0) parts.add(n + " NK");
            if (parts.isEmpty()) parts.add("empty");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parts.get(i));
            }
            return sb.toString();
        }
    }

    private SiteGroups() {
    }

    /** Every point item in the plan (sensors, NK effectors, kinetic effectors). */
    public static List<PointItem> pointItems(PointDefensePlan plan) {
        List<PointItem> out = new ArrayList<>();
        if (plan == null) return out;
        for (ItemKind kind : new ItemKind[] {ItemKind.SENSOR, ItemKind.NON_KINETIC_EFFECTOR, ItemKind.KINETIC_EFFECTOR}) {
            for (BasePlanItem b : plan.<BasePlanItem>listFor(kind)) {
                if (b instanceof PointItem) out.add((PointItem) b);
            }
        }
        return out;
    }

    /** Sites keyed by uid, sorted by name. Items without a site are skipped. */
    public static Map<String, Site> build(Collection<? extends PointItem> items) {
        Map<String, Site> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (items == null) return out;
        for (PointItem p : items) {
            String uid = PointItem.normalizeSiteUid(p.siteUid);
            if (uid == null) continue;
            Site s = out.get(uid);
            if (s == null) {
                s = new Site(uid);
                out.put(uid, s);
            }
            s.members.add(p);
        }
        return out;
    }

    public static Map<String, Site> build(PointDefensePlan plan) {
        return build(pointItems(plan));
    }

    /** Other members of {@code item}'s site (excluding itself, matched by uid). */
    public static List<PointItem> otherMembers(PointDefensePlan plan, PointItem item) {
        List<PointItem> out = new ArrayList<>();
        String site = item == null ? null : PointItem.normalizeSiteUid(item.siteUid);
        if (site == null) return out;
        for (PointItem p : pointItems(plan)) {
            if (!p.uid.equals(item.uid) && site.equalsIgnoreCase(String.valueOf(PointItem.normalizeSiteUid(p.siteUid)))) {
                out.add(p);
            }
        }
        return out;
    }
}
