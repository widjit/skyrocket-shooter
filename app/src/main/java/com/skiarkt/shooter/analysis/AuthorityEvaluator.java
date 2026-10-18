package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.AuthorityType;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.EngagementAuthorityZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.model.PointItem;
import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Engagement-authority rules (ROADMAP R6). An effector class may be recommended / authorized
 * against a threat point only when the point is inside at least one authority zone of that
 * class AND outside every non-hostile (weapons hold) zone. Non-hostile always wins.
 *
 * <p>Kinetic effectors use {@link AuthorityType#KINETIC} zones, NK (RF) effectors use
 * {@link AuthorityType#NON_KINETIC} zones. A kinetic effector whose
 * {@code engagementAuthorityPerimeterUid} names an authority zone is restricted to that zone.
 */
public final class AuthorityEvaluator {
    /** Envelope sampling resolution. */
    static final int RADIAL_STEPS = 24;
    static final double ANGULAR_STEP_DEG = 5.0;

    private AuthorityEvaluator() {
    }

    /** Authority status of a single (threat) point. */
    public static final class PointStatus {
        public final boolean inKineticZone;
        public final boolean inNonKineticZone;
        public final boolean inNonHostileZone;
        public final List<String> zoneLabels;

        PointStatus(boolean k, boolean nk, boolean nh, List<String> labels) {
            inKineticZone = k;
            inNonKineticZone = nk;
            inNonHostileZone = nh;
            zoneLabels = labels;
        }

        public boolean kineticAuthorized() {
            return inKineticZone && !inNonHostileZone;
        }

        public boolean nonKineticAuthorized() {
            return inNonKineticZone && !inNonHostileZone;
        }
    }

    public static PointStatus evaluatePoint(GeoPos p, PointDefensePlan plan) {
        boolean k = false, nk = false, nh = false;
        List<String> labels = new ArrayList<>();
        if (p != null && plan != null && plan.authorityZones != null) {
            for (EngagementAuthorityZone z : plan.authorityZones) {
                if (!inside(p, z, plan)) continue;
                labels.add(z.label);
                if (z.authorityType == AuthorityType.KINETIC) k = true;
                else if (z.authorityType == AuthorityType.NON_KINETIC) nk = true;
                else if (z.authorityType == AuthorityType.NON_HOSTILE) nh = true;
            }
        }
        return new PointStatus(k, nk, nh, labels);
    }

    /** True when {@code p} lies inside zone {@code z} (buffer applied to its resolved base). */
    public static boolean inside(GeoPos p, EngagementAuthorityZone z, PointDefensePlan plan) {
        if (z == null || p == null || z.authorityType == null) return false;
        return PolygonBuffer.contains(p, z.resolveBase(plan), z.bufferMeters);
    }

    // ------------------------------------------------------------ effector check

    public enum Flag {
        /** No authority zone of the effector's class exists (or the linked zone is missing). */
        NO_AUTHORITY_ZONE("no %s zone defined"),
        /** Linked perimeter is an authority zone of the wrong class. */
        LINKED_ZONE_WRONG_CLASS("linked zone is not a %s zone"),
        /** Part of the engagement envelope lies outside the class authority zone(s). */
        ENVELOPE_EXCEEDS_AUTHORITY("envelope extends outside %s"),
        /** Part of the engagement envelope overlaps a weapons-hold zone. */
        ENVELOPE_IN_NON_HOSTILE("envelope overlaps weapons-hold zone"),
        /** The effector itself sits inside a weapons-hold zone. */
        EFFECTOR_IN_NON_HOSTILE("effector is inside a weapons-hold zone");

        private final String fmt;

        Flag(String fmt) {
            this.fmt = fmt;
        }

        public String describe(AuthorityType cls) {
            return String.format(fmt, cls.displayName.toLowerCase(java.util.Locale.US));
        }
    }

    /** Result of checking one effector's engagement envelope against the authority zones. */
    public static final class EffectorCheck {
        public final PointItem effector;
        public final AuthorityType authorityClass;
        /** Fractions of the envelope area (0..1). */
        public final double authorizedFraction;
        public final double nonHostileFraction;
        public final double unauthorizedFraction;
        public final List<Flag> flags;

        EffectorCheck(PointItem e, AuthorityType cls, double auth, double nh, double unauth, List<Flag> flags) {
            effector = e;
            authorityClass = cls;
            authorizedFraction = auth;
            nonHostileFraction = nh;
            unauthorizedFraction = unauth;
            this.flags = flags;
        }

        public boolean hasViolation() {
            return !flags.isEmpty();
        }

        /** "Rifle 1 (Kinetic authority): 82% authorized; envelope extends outside ...". */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(effector.label).append(" (").append(authorityClass.displayName).append("): ")
                    .append(Math.round(authorizedFraction * 100)).append("% authorized");
            if (nonHostileFraction > 0) sb.append(", ").append(Math.round(nonHostileFraction * 100))
                    .append("% weapons hold");
            if (flags.isEmpty()) sb.append(" \u2014 OK");
            for (Flag f : flags) sb.append("\n  \u26A0 ").append(f.describe(authorityClass));
            return sb.toString();
        }
    }

    /** Authority class an effector needs, or null for non-effectors (sensors). */
    public static AuthorityType classFor(BasePlanItem item) {
        if (item instanceof KineticEffector) return AuthorityType.KINETIC;
        if (item instanceof NonKineticEffector) return AuthorityType.NON_KINETIC;
        return null;
    }

    /** Checks every kinetic and NK effector in the plan. */
    public static List<EffectorCheck> checkAll(PointDefensePlan plan) {
        List<EffectorCheck> out = new ArrayList<>();
        if (plan == null) return out;
        for (KineticEffector k : plan.kineticEffectors) {
            EffectorCheck c = checkEffector(k, plan);
            if (c != null) out.add(c);
        }
        for (NonKineticEffector n : plan.nonKineticEffectors) {
            EffectorCheck c = checkEffector(n, plan);
            if (c != null) out.add(c);
        }
        return out;
    }

    public static EffectorCheck checkEffector(PointItem eff, PointDefensePlan plan) {
        AuthorityType cls = classFor(eff);
        if (cls == null || eff.position == null || !eff.position.isValid() || plan == null) return null;
        List<Flag> flags = new ArrayList<>();

        // Authority zones applicable to this effector.
        List<EngagementAuthorityZone> authZones = new ArrayList<>();
        List<EngagementAuthorityZone> holdZones = new ArrayList<>();
        EngagementAuthorityZone linked = null;
        if (eff instanceof KineticEffector) {
            String uid = ((KineticEffector) eff).engagementAuthorityPerimeterUid;
            BasePlanItem b = plan.findByUid(uid);
            if (b instanceof EngagementAuthorityZone) linked = (EngagementAuthorityZone) b;
        }
        for (EngagementAuthorityZone z : plan.authorityZones) {
            if (z == null || z.authorityType == null || !PolygonBuffer.usable(z.resolveBase(plan))) continue;
            if (z.authorityType == AuthorityType.NON_HOSTILE) holdZones.add(z);
            else if (linked == null && z.authorityType == cls) authZones.add(z);
        }
        if (linked != null) {
            if (linked.authorityType != cls) flags.add(Flag.LINKED_ZONE_WRONG_CLASS);
            else authZones.add(linked);
        }
        if (authZones.isEmpty() && !flags.contains(Flag.LINKED_ZONE_WRONG_CLASS)) flags.add(Flag.NO_AUTHORITY_ZONE);

        for (EngagementAuthorityZone h : holdZones) {
            if (inside(eff.position, h, plan)) {
                flags.add(Flag.EFFECTOR_IN_NON_HOSTILE);
                break;
            }
        }

        // Area-weighted polar sampling of the engagement envelope.
        double maxR = eff.maxEngagementDistMeters;
        double minR = eff instanceof KineticEffector ? ((KineticEffector) eff).minEngagementRange : 0.0;
        double wAuth = 0, wHold = 0, wUnauth = 0;
        if (maxR > 0 && maxR > minR) {
            double dr = (maxR - minR) / RADIAL_STEPS;
            for (int i = 0; i < RADIAL_STEPS; i++) {
                double r = minR + (i + 0.5) * dr;
                for (double brg = ANGULAR_STEP_DEG / 2; brg < 360; brg += ANGULAR_STEP_DEG) {
                    if (eff instanceof KineticEffector && !((KineticEffector) eff).isBearingAllowed(brg)) continue;
                    GeoPos p = GeoUtils.destination(eff.position, brg, r);
                    boolean hold = false;
                    for (EngagementAuthorityZone h : holdZones) {
                        if (inside(p, h, plan)) {
                            hold = true;
                            break;
                        }
                    }
                    if (hold) {
                        wHold += r;
                        continue;
                    }
                    boolean auth = false;
                    for (EngagementAuthorityZone z : authZones) {
                        if (inside(p, z, plan)) {
                            auth = true;
                            break;
                        }
                    }
                    if (auth) wAuth += r;
                    else wUnauth += r;
                }
            }
        }
        double total = wAuth + wHold + wUnauth;
        double fa = total > 0 ? wAuth / total : 0, fh = total > 0 ? wHold / total : 0;
        double fu = total > 0 ? wUnauth / total : 0;
        if (fh > 0) flags.add(Flag.ENVELOPE_IN_NON_HOSTILE);
        if (fu > 0 && !authZones.isEmpty()) flags.add(Flag.ENVELOPE_EXCEEDS_AUTHORITY);
        return new EffectorCheck(eff, cls, fa, fh, fu, flags);
    }
}
