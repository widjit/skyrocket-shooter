package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.settings.UnitSystem;
import com.skiarkt.shooter.util.UnitConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of {@link InterceptAltitudeCalculator}: the minimum safe intercept altitude along
 * an ingress path, the per-sample profile and contiguous constraint segments. All values are
 * meters (HAE unless named AGL); use {@link #format} / {@link #formatSegment} for display.
 */
public final class InterceptAltitudeResult {

    /** One sample along the path. */
    public static final class Sample {
        /** Distance from the path start (m). */
        public final double distanceMeters;
        public final GeoPos position;
        /** Terrain HAE at the sample (NaN when no DTED). */
        public final double groundHae;
        /** Highest exclusion-zone roof touching this sample (NaN = unconstrained). */
        public final double roofHae;
        /** roofHae + safety buffer (NaN = unconstrained). */
        public final double minSafeHae;
        public final String zoneUid;
        public final String zoneLabel;

        public Sample(double distanceMeters, GeoPos position, double groundHae, double roofHae,
                double minSafeHae, String zoneUid, String zoneLabel) {
            this.distanceMeters = distanceMeters;
            this.position = position;
            this.groundHae = groundHae;
            this.roofHae = roofHae;
            this.minSafeHae = minSafeHae;
            this.zoneUid = zoneUid;
            this.zoneLabel = zoneLabel;
        }

        public boolean isConstrained() {
            return !Double.isNaN(minSafeHae);
        }

        /** Minimum safe altitude above local terrain at this sample (NaN if unknown). */
        public double minSafeAgl() {
            return isConstrained() && !Double.isNaN(groundHae) ? minSafeHae - groundHae : Double.NaN;
        }
    }

    /** Contiguous run of samples governed by the same zone (or by none). */
    public static final class ConstraintSegment {
        public final double startMeters;
        public final double endMeters;
        public final String zoneUid;
        public final String zoneLabel;
        /** Highest min-safe altitude within the run (NaN when unconstrained). */
        public final double maxMinSafeHae;
        public final GeoPos start;
        public final GeoPos end;

        ConstraintSegment(double startMeters, double endMeters, String zoneUid, String zoneLabel,
                double maxMinSafeHae, GeoPos start, GeoPos end) {
            this.startMeters = startMeters;
            this.endMeters = endMeters;
            this.zoneUid = zoneUid;
            this.zoneLabel = zoneLabel;
            this.maxMinSafeHae = maxMinSafeHae;
            this.start = start;
            this.end = end;
        }

        public boolean isConstrained() {
            return zoneUid != null;
        }
    }

    private final String sourceUid;
    private final String sourceLabel;
    private final List<GeoPos> path;
    private final double totalLengthMeters;
    private final double sampleSpacingMeters;
    private final double safetyBufferMeters;
    private final double halfWidthMeters;
    private final List<Sample> samples;
    private final List<String> constrainingZoneUids;
    private final boolean terrainComplete;
    private final int criticalIndex;
    private final List<ConstraintSegment> segments;

    InterceptAltitudeResult(String sourceUid, String sourceLabel, List<GeoPos> path, double totalLengthMeters,
            double sampleSpacingMeters, double safetyBufferMeters, double halfWidthMeters, List<Sample> samples,
            List<String> constrainingZoneUids, boolean terrainComplete) {
        this.sourceUid = sourceUid;
        this.sourceLabel = sourceLabel;
        this.path = Collections.unmodifiableList(path);
        this.totalLengthMeters = totalLengthMeters;
        this.sampleSpacingMeters = sampleSpacingMeters;
        this.safetyBufferMeters = safetyBufferMeters;
        this.halfWidthMeters = halfWidthMeters;
        this.samples = Collections.unmodifiableList(samples);
        this.constrainingZoneUids = constrainingZoneUids;
        this.terrainComplete = terrainComplete;

        int crit = -1;
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            if (s.isConstrained() && (crit < 0 || s.minSafeHae > samples.get(crit).minSafeHae)) crit = i;
        }
        this.criticalIndex = crit;
        this.segments = Collections.unmodifiableList(buildSegments(samples));
    }

    private static List<ConstraintSegment> buildSegments(List<Sample> samples) {
        List<ConstraintSegment> out = new ArrayList<>();
        int i = 0;
        while (i < samples.size()) {
            Sample first = samples.get(i);
            int j = i;
            double max = first.minSafeHae;
            while (j + 1 < samples.size() && same(samples.get(j + 1).zoneUid, first.zoneUid)) {
                j++;
                double v = samples.get(j).minSafeHae;
                if (!Double.isNaN(v) && (Double.isNaN(max) || v > max)) max = v;
            }
            Sample last = samples.get(j);
            out.add(new ConstraintSegment(first.distanceMeters, last.distanceMeters, first.zoneUid,
                    first.zoneLabel, max, first.position, last.position));
            i = j + 1;
        }
        return out;
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---------------------------------------------------------------- accessors

    /** True when at least one exclusion zone constrains the path. */
    public boolean hasConstraint() {
        return criticalIndex >= 0;
    }

    /** Minimum safe intercept altitude, meters HAE (NaN when no zone constrains the path). */
    public double getMinInterceptAltitudeHae() {
        return hasConstraint() ? samples.get(criticalIndex).minSafeHae : Double.NaN;
    }

    /** Minimum safe intercept altitude above terrain at the critical sample (NaN if unknown). */
    public double getMinInterceptAltitudeAgl() {
        return hasConstraint() ? samples.get(criticalIndex).minSafeAgl() : Double.NaN;
    }

    /** Highest roof (without safety buffer), meters HAE. */
    public double getMaxRoofHae() {
        return hasConstraint() ? samples.get(criticalIndex).roofHae : Double.NaN;
    }

    /** The sample that sets the minimum intercept altitude, or null. */
    public Sample getCriticalSample() {
        return hasConstraint() ? samples.get(criticalIndex) : null;
    }

    public String getControllingZoneUid() {
        return hasConstraint() ? samples.get(criticalIndex).zoneUid : null;
    }

    public String getControllingZoneLabel() {
        return hasConstraint() ? samples.get(criticalIndex).zoneLabel : null;
    }

    public String getSourceUid() {
        return sourceUid;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public List<GeoPos> getPath() {
        return path;
    }

    public double getTotalLengthMeters() {
        return totalLengthMeters;
    }

    /** Spacing actually used (may exceed the requested spacing for very long paths). */
    public double getSampleSpacingMeters() {
        return sampleSpacingMeters;
    }

    public double getSafetyBufferMeters() {
        return safetyBufferMeters;
    }

    public double getHalfWidthMeters() {
        return halfWidthMeters;
    }

    public List<Sample> getProfile() {
        return samples;
    }

    public List<ConstraintSegment> getSegments() {
        return segments;
    }

    public List<String> getConstrainingZoneUids() {
        return constrainingZoneUids;
    }

    /** False when any zone or sample lacked DTED (altitudes then assume 0 m HAE terrain there). */
    public boolean isTerrainComplete() {
        return terrainComplete;
    }

    // ---------------------------------------------------------------- formatting

    /** Headline, e.g. "165.0 m HAE (55.0 m AGL)" or "No exclusion zone constraint". */
    public String formatAltitude(UnitSystem unit, int precision) {
        if (!hasConstraint()) return "No exclusion zone constraint";
        String s = UnitConverter.format(getMinInterceptAltitudeHae(), unit, precision) + " HAE";
        double agl = getMinInterceptAltitudeAgl();
        if (!Double.isNaN(agl)) s += " (" + UnitConverter.format(agl, unit, precision) + " AGL)";
        return s;
    }

    /** Multi-line summary for the side panel. */
    public String format(UnitSystem unit, int precision) {
        StringBuilder sb = new StringBuilder();
        sb.append("Min safe intercept altitude: ").append(formatAltitude(unit, precision)).append('\n');
        sb.append("Path: ").append(sourceLabel == null ? "Ingress line" : sourceLabel)
                .append(", ").append(UnitConverter.format(totalLengthMeters, unit, precision));
        if (halfWidthMeters > 0) {
            sb.append(", width ").append(UnitConverter.format(halfWidthMeters * 2, unit, precision));
        }
        sb.append('\n');
        if (hasConstraint()) {
            Sample c = getCriticalSample();
            sb.append("Controlling zone: ").append(c.zoneLabel)
                    .append(" at ").append(UnitConverter.format(c.distanceMeters, unit, precision)).append('\n');
            sb.append("Highest roof: ").append(UnitConverter.format(c.roofHae, unit, precision))
                    .append(" HAE + buffer ").append(UnitConverter.format(safetyBufferMeters, unit, precision))
                    .append('\n');
            sb.append("Zones crossed: ").append(constrainingZoneUids.size()).append('\n');
        }
        sb.append("Samples: ").append(samples.size()).append(" every ")
                .append(UnitConverter.format(sampleSpacingMeters, unit, precision));
        if (!terrainComplete) sb.append("\nWARNING: incomplete DTED coverage - missing terrain assumed 0 HAE");
        return sb.toString();
    }

    /** One profile line, e.g. "120.0 m - 300.0 m: Bldg A >= 165.0 m HAE". */
    public static String formatSegment(ConstraintSegment s, UnitSystem unit, int precision) {
        String range = UnitConverter.format(s.startMeters, unit, precision) + " - "
                + UnitConverter.format(s.endMeters, unit, precision) + ": ";
        if (!s.isConstrained()) return range + "clear";
        return range + s.zoneLabel + " \u2265 " + UnitConverter.format(s.maxMinSafeHae, unit, precision) + " HAE";
    }
}
