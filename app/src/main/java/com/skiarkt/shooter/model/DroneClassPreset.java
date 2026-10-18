package com.skiarkt.shooter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Drone-class band presets (R1): which control / video / navigation bands a threat class
 * typically uses. Applying a preset fills a jammer's band list with these frequencies; EIRP,
 * threshold and range come from the effector's current single-band values.
 */
public enum DroneClassPreset {
    CONSUMER_QUAD("Consumer quad (2.4 / 5.8 GHz + GNSS L1)",
            new String[]{"2.4 GHz C2", "5.8 GHz video", "GNSS L1"},
            new double[]{2437.0, 5785.0, 1575.42}),
    LONG_RANGE_FPV("Long-range FPV (433 / 915 MHz + 5.8 GHz)",
            new String[]{"433 MHz C2", "915 MHz C2", "5.8 GHz video"},
            new double[]{433.0, 915.0, 5785.0}),
    FIXED_WING_GNSS("Fixed wing / GNSS-guided (915 MHz + L1/L2)",
            new String[]{"915 MHz C2", "GNSS L1", "GNSS L2"},
            new double[]{915.0, 1575.42, 1227.60}),
    FULL_SPECTRUM("Full spectrum (433 / 915 / 2.4 / 5.8 + L1/L2)",
            new String[]{"433 MHz", "915 MHz", "2.4 GHz", "5.8 GHz", "GNSS L1", "GNSS L2"},
            new double[]{433.0, 915.0, 2437.0, 5785.0, 1575.42, 1227.60});

    public final String displayName;
    private final String[] labels;
    private final double[] frequenciesMhz;

    DroneClassPreset(String displayName, String[] labels, double[] frequenciesMhz) {
        this.displayName = displayName;
        this.labels = labels;
        this.frequenciesMhz = frequenciesMhz;
    }

    public int bandCount() {
        return frequenciesMhz.length;
    }

    /** Bands for this class with the given defaults. */
    public List<RfBand> bands(double eirpDbm, double thresholdDbm, double maxRangeM) {
        List<RfBand> out = new ArrayList<>();
        for (int i = 0; i < frequenciesMhz.length; i++) {
            out.add(new RfBand(labels[i], frequenciesMhz[i], eirpDbm, thresholdDbm, maxRangeM, true));
        }
        return out;
    }
}
