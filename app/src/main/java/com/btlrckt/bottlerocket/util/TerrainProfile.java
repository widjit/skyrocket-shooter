package com.btlrckt.bottlerocket.util;

import com.btlrckt.bottlerocket.model.GeoPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Terrain samples along a great-circle path between two points. */
public class TerrainProfile {
    public static class Sample {
        public final double distanceMeters;
        public final GeoPos position;
        /** Meters HAE; NaN when no elevation data. */
        public final double elevationHae;

        public Sample(double distanceMeters, GeoPos position, double elevationHae) {
            this.distanceMeters = distanceMeters;
            this.position = position;
            this.elevationHae = elevationHae;
        }

        public boolean hasElevation() {
            return !Double.isNaN(elevationHae);
        }
    }

    private final List<Sample> samples;
    private final double totalDistanceMeters;

    public TerrainProfile(List<Sample> samples, double totalDistanceMeters) {
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public boolean isComplete() {
        for (Sample s : samples) if (!s.hasElevation()) return false;
        return !samples.isEmpty();
    }

    public int missingSampleCount() {
        int n = 0;
        for (Sample s : samples) if (!s.hasElevation()) n++;
        return n;
    }

    public double maxElevation() {
        double m = Double.NaN;
        for (Sample s : samples) {
            if (s.hasElevation() && (Double.isNaN(m) || s.elevationHae > m)) m = s.elevationHae;
        }
        return m;
    }

    public double minElevation() {
        double m = Double.NaN;
        for (Sample s : samples) {
            if (s.hasElevation() && (Double.isNaN(m) || s.elevationHae < m)) m = s.elevationHae;
        }
        return m;
    }
}
