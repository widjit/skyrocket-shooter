package com.skiarkt.shooter.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.util.ElevationSource;
import com.skiarkt.shooter.util.GeoUtils;

import org.junit.Test;

import java.util.List;

public class Phase3AnalysisTest {
    private static final GeoPos C = new GeoPos(35.0, -117.0);

    @Test
    public void fsplAndClassification() {
        assertEquals(100.05, RfPropagation.fsplDb(1000, 2400), 0.02);
        assertEquals(-53.05, RfPropagation.receivedPowerDbm(47, 1000, 2400), 0.02);
        assertEquals(-93.05, RfPropagation.receivedPowerDbm(47, 1000, 2400, false, 40), 0.02);
        assertEquals(RfPropagation.Effectiveness.EFFECTIVE, RfPropagation.classify(-70, -70));
        assertEquals(RfPropagation.Effectiveness.MARGINAL, RfPropagation.classify(-75, -70));
        assertEquals(RfPropagation.Effectiveness.INSUFFICIENT, RfPropagation.classify(-81, -70));
        double r = RfPropagation.rangeForThresholdM(47, -70, 2400);
        assertEquals(-70, RfPropagation.receivedPowerDbm(47, r, 2400), 1e-6);
        assertEquals(11, RfPropagation.band(-60, -70, 10));
        assertEquals(0, RfPropagation.band(-90, -70, 10));
        assertEquals(0xFF00FF00, RfPropagation.colorFor(-10, -70, 0x00FF00, 0xFFFF00, 0xFF0000));
    }

    @Test
    public void sectorGeometry() {
        assertTrue(CoverageGeometry.bearingInSector(355, 10, 40));
        assertFalse(CoverageGeometry.bearingInSector(340, 10, 40));
        assertEquals(350, CoverageGeometry.sectorStartDeg(10, 40), 1e-9);
        List<GeoPos> sector = CoverageGeometry.coverage(C, 1000, 90, 60, 2);
        assertEquals(C.lat, sector.get(0).lat, 1e-12);
        assertEquals(60, GeoUtils.bearingDeg(C, sector.get(1)), 0.05);
        assertEquals(120, GeoUtils.bearingDeg(C, sector.get(sector.size() - 1)), 0.05);
        for (GeoPos p : CoverageGeometry.circle(C, 500, 5)) assertEquals(500, GeoUtils.distanceMeters(C, p), 0.5);
    }

    /** Flat terrain with a 100 m ridge 490-530 m east of the centre. */
    private static final ElevationSource RIDGE = new ElevationSource() {
        @Override
        public double getElevationHae(double lat, double lon) {
            GeoPos p = new GeoPos(lat, lon);
            double d = GeoUtils.distanceMeters(C, p);
            double b = GeoUtils.bearingDeg(C, p);
            boolean east = d > 1 && Math.abs(b - 90) < 15;
            return east && d >= 490 && d <= 530 ? 100 : 0;
        }
    };

    @Test
    public void losSweepMasksBehindRidge() {
        LosSweep.Result r = LosSweep.compute(RIDGE, C, 2, 2, 1000, 0, 360, 5, 20);
        assertTrue(r.terrainAvailable);
        assertTrue(r.isVisible(90, 400));
        assertFalse(r.isVisible(90, 800));
        assertTrue(r.isVisible(270, 800));
        assertTrue(r.visibleFraction() < 1.0 && r.visibleFraction() > 0.8);
        assertFalse(r.maskedWedges().isEmpty());
        assertTrue(r.maskedWedges().size() < r.maskedSampleCount());

        LosSweep.Result none = LosSweep.compute(null, C, 2, 2, 1000, 90, 60, 5, 20);
        assertFalse(none.terrainAvailable);
        assertEquals(1.0, none.visibleFraction(), 1e-9);
        assertFalse(none.isVisible(270, 100)); // outside sector
    }

    @Test
    public void heatmapUsesTerrainAndMerges() {
        RfHeatmap.Params p = new RfHeatmap.Params();
        p.radiusM = 1000;
        p.txDbm = 20;
        p.thresholdDbm = -70;
        LosSweep.Result los = LosSweep.compute(RIDGE, C, 2, 2, 1000, 0, 360, 5, 20);
        RfHeatmap.Raster r = RfHeatmap.compute(C, p, los);
        assertEquals(100, r.size);
        assertEquals(-1, r.band(0, 0)); // corner outside circle
        // west of centre (LOS) vs. east behind ridge at same distance: terrain loss applied
        float west = r.rxDbm[50 * 100 + 10], east = r.rxDbm[50 * 100 + 89];
        assertEquals(p.terrainLossDb, west - east, 0.5);
        assertTrue(r.effectiveFraction > 0 && r.effectiveFraction + r.marginalFraction <= 1.0);
        RfHeatmap.Raster d = r.downsample(4);
        assertEquals(25, d.size);
        assertEquals(r.north, d.north, 1e-9);
        int cells = 0;
        for (RfHeatmap.Rect rect : RfHeatmap.mergeRects(r.bands, r.size, r.size)) {
            cells += (rect.col1 - rect.col0) * (rect.row1 - rect.row0);
        }
        int inside = 0;
        for (int b : r.bands) if (b >= 0) inside++;
        assertEquals(inside, cells);
    }
}
