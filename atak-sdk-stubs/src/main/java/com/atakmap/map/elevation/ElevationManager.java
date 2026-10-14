/* Compile-only stub mirroring the CivTAK 4.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.map.elevation;

public final class ElevationManager {
    private ElevationManager() {
    }

    /** @return terrain elevation in meters HAE, or NaN when no elevation data covers the point. */
    public static double getElevation(double latitude, double longitude, QueryParameters filter) {
        return Double.NaN;
    }

    public static class QueryParameters {
        public QueryParameters() {
        }
    }
}
