package com.skiarkt.shooter.util;

/** Abstraction over terrain elevation lookup (ATAK in production, fakes in tests). */
public interface ElevationSource {
    /** @return terrain elevation in meters HAE, or NaN when no data covers the point. */
    double getElevationHae(double lat, double lon);
}
