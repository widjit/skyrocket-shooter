package com.skiarkt.shooter.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.settings.UnitSystem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PngExportModelTest {
    private static final long T = 1792086200000L; // 2026-10-15T17:43:20Z

    @Test
    public void fileNameIsSafeAndUtcStamped() {
        PointDefensePlan p = new PointDefensePlan();
        p.name = "FOB Bravo / North!";
        assertEquals("FOB_Bravo_North_20261015-174320Z.png", PngExportModel.fileName(p, T));
        p.name = "  ";
        assertEquals("plan_20261015-174320Z.png", PngExportModel.fileName(p, T));
        assertEquals("15 OCT 2026 1743Z", PngExportModel.dtg(T));
    }

    @Test
    public void niceScale() {
        assertEquals(500, PngExportModel.niceScaleMeters(730), 0);
        assertEquals(2000, PngExportModel.niceScaleMeters(2000), 0);
        assertEquals(100, PngExportModel.niceScaleMeters(199), 0);
        assertEquals(1, PngExportModel.niceScaleMeters(0.3), 0);
    }

    private static PointDefensePlan plan() {
        PointDefensePlan p = new PointDefensePlan();
        SensorItem s = new SensorItem();
        s.label = "R1";
        s.position = new GeoPos(35.0, -117.0);
        s.rangeMeters = 1000;
        p.sensors.add(s);
        ExclusionZone z = new ExclusionZone();
        z.label = "Fuel";
        z.heightAglMeters = 30;
        z.points = Arrays.asList(new GeoPos(35.001, -117.001), new GeoPos(35.001, -116.999), new GeoPos(34.999, -117));
        p.exclusionZones.add(z);
        return p;
    }

    @Test
    public void summaryUsesDisplayUnits() {
        List<String> lines = PngExportModel.summaryLines(plan(), UnitSystem.KILOMETERS, 1);
        assertEquals("Exclusion Zone (1)", lines.get(0));
        assertEquals("  Fuel - height 0.0 km AGL", lines.get(1));
        assertEquals("Sensor (1)", lines.get(2));
        assertTrue(lines.get(3), lines.get(3).startsWith("  R1 - RADAR, range 1.0 km, 360"));
        assertEquals(Arrays.asList("No plan items"),
                PngExportModel.summaryLines(new PointDefensePlan(), UnitSystem.METERS, 1));
        assertEquals(2, PngExportModel.legend(plan()).size());
    }

    @Test
    public void projectionFitsCoverageInsideRect() {
        PngExportModel.Projection pr = PngExportModel.Projection.fit(plan(), 0, 100, 1600, 1200, 60);
        assertNotNull(pr);
        GeoPos c = new GeoPos(35.0, -117.0);
        float r = pr.pixels(1000);
        // The 1 km sensor ring is the widest geometry; it must fit with the margin, north up.
        assertTrue(pr.x(c) - r >= 59 && pr.x(c) + r <= 1541);
        assertTrue(pr.y(c) - r >= 159 && pr.y(c) + r <= 1241);
        assertTrue(pr.y(new GeoPos(35.001, -117)) < pr.y(c));
        assertTrue(pr.x(new GeoPos(35.0, -116.999)) > pr.x(c));
        assertNull(PngExportModel.Projection.fit(new PointDefensePlan(), 0, 0, 100, 100, 10));
    }
}
