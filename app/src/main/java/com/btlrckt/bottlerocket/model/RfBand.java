package com.btlrckt.bottlerocket.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One jamming band of a multi-band {@link NonKineticEffector} (R1). An effector with an empty
 * band list behaves exactly like the legacy single-band jammer
 * ({@link NonKineticEffector#frequencyMhz} / {@link NonKineticEffector#eirpDbm}).
 */
public class RfBand {
    public String label = "";
    public double frequencyMhz = 2400.0;
    /** EIRP on this band (dBm). */
    public double eirpDbm = 40.0;
    /** Minimum received jamming power considered effective on this band (dBm). */
    public double thresholdDbm = -70.0;
    /** Max effective range (meters); 0 = limited only by the effector max engagement distance. */
    public double maxRangeMeters = 0.0;
    /** Shown / included in the combined heat map. */
    public boolean enabled = true;

    public RfBand() {
    }

    public RfBand(String label, double frequencyMhz, double eirpDbm, double thresholdDbm,
            double maxRangeMeters, boolean enabled) {
        this.label = label == null ? "" : label;
        this.frequencyMhz = frequencyMhz;
        this.eirpDbm = eirpDbm;
        this.thresholdDbm = thresholdDbm;
        this.maxRangeMeters = maxRangeMeters;
        this.enabled = enabled;
    }

    public RfBand copy() {
        return new RfBand(label, frequencyMhz, eirpDbm, thresholdDbm, maxRangeMeters, enabled);
    }

    public boolean isValid() {
        return frequencyMhz > 0 && !Double.isNaN(eirpDbm) && !Double.isInfinite(eirpDbm)
                && !Double.isNaN(thresholdDbm) && !Double.isInfinite(thresholdDbm)
                && maxRangeMeters >= 0 && !Double.isInfinite(maxRangeMeters);
    }

    /** Effective range cap given the effector's max engagement distance. */
    public double rangeCapMeters(double effectorMaxM) {
        return maxRangeMeters > 0 ? Math.min(maxRangeMeters, effectorMaxM) : effectorMaxM;
    }

    /** Display name: label or the frequency. */
    public String displayName() {
        if (label != null && !label.trim().isEmpty()) return label.trim();
        return frequencyMhz >= 1000
                ? String.format(Locale.US, "%.3g GHz", frequencyMhz / 1000.0)
                : String.format(Locale.US, "%.0f MHz", frequencyMhz);
    }

    /** Stable text for cache keys and equality checks. */
    public String key() {
        return String.format(Locale.US, "%.3f:%.2f:%.2f:%.1f:%d", frequencyMhz, eirpDbm, thresholdDbm,
                maxRangeMeters, enabled ? 1 : 0);
    }

    // ---------------------------------------------------------------- encoding

    /**
     * Compact list encoding used by Parcel and CoT:
     * {@code freq:eirp:thr:range:enabled:label;...} (label percent-escaped for ':' ';' '%').
     */
    public static String encodeList(List<RfBand> bands) {
        if (bands == null || bands.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RfBand b : bands) {
            if (b == null) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%s:%s:%s:%s:%d:%s", num(b.frequencyMhz), num(b.eirpDbm),
                    num(b.thresholdDbm), num(b.maxRangeMeters), b.enabled ? 1 : 0, escape(b.label)));
        }
        return sb.toString();
    }

    /** Lenient decode; malformed entries are skipped. Never returns null. */
    public static List<RfBand> decodeList(String s) {
        List<RfBand> out = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return out;
        for (String entry : s.split(";")) {
            String[] f = entry.split(":", -1);
            if (f.length < 4) continue;
            try {
                RfBand b = new RfBand();
                b.frequencyMhz = Double.parseDouble(f[0].trim());
                b.eirpDbm = Double.parseDouble(f[1].trim());
                b.thresholdDbm = Double.parseDouble(f[2].trim());
                b.maxRangeMeters = Double.parseDouble(f[3].trim());
                b.enabled = f.length < 5 || !"0".equals(f[4].trim());
                b.label = f.length >= 6 ? unescape(f[5]) : "";
                if (b.isValid()) out.add(b);
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return out;
    }

    private static String num(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e12 ? String.valueOf((long) v) : Double.toString(v);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace(":", "%3A").replace(";", "%3B");
    }

    private static String unescape(String s) {
        return s.replace("%3B", ";").replace("%3A", ":").replace("%25", "%");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RfBand)) return false;
        RfBand b = (RfBand) o;
        return key().equals(b.key()) && String.valueOf(label).equals(String.valueOf(b.label));
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
