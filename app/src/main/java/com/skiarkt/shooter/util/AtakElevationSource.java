package com.skiarkt.shooter.util;

import com.atakmap.map.elevation.ElevationManager;

/**
 * Production {@link ElevationSource} backed by ATAK's {@link ElevationManager}, which
 * aggregates every installed elevation source (DTED 0/1/2, SRTM, imported terrain).
 */
public class AtakElevationSource implements ElevationSource {
    @Override
    public double getElevationHae(double lat, double lon) {
        try {
            return ElevationManager.getElevation(lat, lon, (ElevationManager.QueryParameters) null);
        } catch (Throwable t) {
            // Elevation subsystem unavailable (e.g. not yet initialised) -> treat as no data.
            return Double.NaN;
        }
    }
}
