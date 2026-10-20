package com.btlrckt.bottlerocket.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.btlrckt.bottlerocket.R;
import com.btlrckt.bottlerocket.data.PointDefenseRepository;
import com.btlrckt.bottlerocket.model.PointDefensePlan;
import com.btlrckt.bottlerocket.settings.SettingsManager;
import com.btlrckt.bottlerocket.settings.UnitSystem;

/** Settings + plan metadata editor (fully functional in Phase 1). */
public class SettingsPanel {
    private static final double SAMPLE_METERS = 1000.0;

    private final Context pluginContext;
    private final SettingsManager settings;
    private final PointDefenseRepository repository;
    private final Runnable onBack;

    private RadioGroup unitGroup;
    private RadioGroup precisionGroup;
    private SeekBar heatmapSeek;
    private SeekBar zoneSeek;
    private TextView heatmapLabel;
    private TextView zoneLabel;
    private TextView preview;
    private EditText planName;
    private EditText planAuthor;
    private EditText planClassification;
    private EditText staleDays;
    // Phase 3: RF / LOS
    private EditText rfFrequency;
    private EditText rfThreshold;
    private EditText rfTerrainLoss;
    private EditText losRangeStep;
    private EditText losAngularStep;
    private EditText losTargetAgl;
    private String rangeStepPrefill;
    private String targetAglPrefill;

    public SettingsPanel(Context pluginContext, SettingsManager settings, PointDefenseRepository repository,
            Runnable onBack) {
        this.pluginContext = pluginContext;
        this.settings = settings;
        this.repository = repository;
        this.onBack = onBack;
    }

    public View createView() {
        View v = PluginLayoutInflater.inflate(pluginContext, R.layout.settings_layout, null);
        unitGroup = v.findViewById(R.id.unit_group);
        precisionGroup = v.findViewById(R.id.precision_group);
        heatmapSeek = v.findViewById(R.id.heatmap_transparency);
        zoneSeek = v.findViewById(R.id.zone_transparency);
        heatmapLabel = v.findViewById(R.id.heatmap_transparency_label);
        zoneLabel = v.findViewById(R.id.zone_transparency_label);
        preview = v.findViewById(R.id.unit_preview);
        planName = v.findViewById(R.id.plan_name);
        planAuthor = v.findViewById(R.id.plan_author);
        planClassification = v.findViewById(R.id.plan_classification);
        staleDays = v.findViewById(R.id.cot_stale_days);
        rfFrequency = v.findViewById(R.id.rf_frequency);
        rfThreshold = v.findViewById(R.id.rf_threshold);
        rfTerrainLoss = v.findViewById(R.id.rf_terrain_loss);
        losRangeStep = v.findViewById(R.id.los_range_step);
        losAngularStep = v.findViewById(R.id.los_angular_step);
        losTargetAgl = v.findViewById(R.id.los_target_agl);
        new SensorPresetsUi(pluginContext, repository.getPresetStore(),
                (android.widget.LinearLayout) v.findViewById(R.id.sensor_presets_container)).populate();
        String unit = settings.getUnitSystem().abbreviation;
        ((TextView) v.findViewById(R.id.los_range_step_label))
                .setText(pluginContext.getString(R.string.settings_los_range_step_fmt, unit));
        ((TextView) v.findViewById(R.id.los_target_agl_label))
                .setText(pluginContext.getString(R.string.settings_los_target_agl_fmt, unit));

        loadValues();

        RadioGroup.OnCheckedChangeListener previewUpdater = new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                updatePreview();
            }
        };
        unitGroup.setOnCheckedChangeListener(previewUpdater);
        precisionGroup.setOnCheckedChangeListener(previewUpdater);
        SeekBar.OnSeekBarChangeListener seekUpdater = new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSeekLabels();
            }
        };
        heatmapSeek.setOnSeekBarChangeListener(seekUpdater);
        zoneSeek.setOnSeekBarChangeListener(seekUpdater);

        v.findViewById(R.id.btn_settings_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
                onBack.run();
            }
        });
        v.findViewById(R.id.btn_settings_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                settings.resetToDefaults();
                loadValues();
            }
        });
        v.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBack.run();
            }
        });
        return v;
    }

    private void loadValues() {
        unitGroup.check(unitRadioId(settings.getUnitSystem()));
        precisionGroup.check(precisionRadioId(settings.getPrecision()));
        heatmapSeek.setProgress(Math.round(settings.getHeatmapTransparency() * 100));
        zoneSeek.setProgress(Math.round(settings.getZoneFillTransparency() * 100));
        PointDefensePlan plan = repository.getPlan();
        planName.setText(plan.name);
        planAuthor.setText(plan.author.isEmpty() ? settings.getDefaultAuthor() : plan.author);
        planClassification.setText(plan.classification);
        staleDays.setText(String.valueOf(settings.getCotStaleDays()));
        rfFrequency.setText(fmt(settings.getRfFrequencyMhz()));
        rfThreshold.setText(fmt(settings.getRfThresholdDbm()));
        rfTerrainLoss.setText(fmt(settings.getRfTerrainLossDb()));
        losAngularStep.setText(fmt(settings.getLosAngularStepDeg()));
        rangeStepPrefill = settings.formatNumber(settings.getLosRangeStepMeters());
        targetAglPrefill = settings.formatNumber(settings.getLosTargetAglMeters());
        losRangeStep.setText(rangeStepPrefill);
        losTargetAgl.setText(targetAglPrefill);
        updateSeekLabels();
        updatePreview();
    }

    private void save() {
        // Distances are parsed in the unit shown when the form was built, before any unit change.
        String rs = losRangeStep.getText().toString().trim();
        if (!rs.equals(rangeStepPrefill)) {
            double m = settings.parseDistance(rs);
            if (!Double.isNaN(m) && m > 0) settings.setLosRangeStepMeters(m);
        }
        String ta = losTargetAgl.getText().toString().trim();
        if (!ta.equals(targetAglPrefill)) {
            double m = settings.parseDistance(ta);
            if (!Double.isNaN(m) && m >= 0) settings.setLosTargetAglMeters(m);
        }
        double f = parse(rfFrequency);
        if (!Double.isNaN(f) && f > 0) settings.setRfFrequencyMhz(f);
        double thr = parse(rfThreshold);
        if (!Double.isNaN(thr)) settings.setRfThresholdDbm(thr);
        double loss = parse(rfTerrainLoss);
        if (!Double.isNaN(loss) && loss >= 0) settings.setRfTerrainLossDb(loss);
        double ang = parse(losAngularStep);
        if (!Double.isNaN(ang) && ang > 0) settings.setLosAngularStepDeg(ang);

        settings.setUnitSystem(selectedUnit());
        settings.setPrecision(selectedPrecision());
        settings.setHeatmapTransparency(heatmapSeek.getProgress() / 100f);
        settings.setZoneFillTransparency(zoneSeek.getProgress() / 100f);
        try {
            settings.setCotStaleDays(Integer.parseInt(staleDays.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
            // keep previous value
        }
        repository.setCotStaleDays(settings.getCotStaleDays());
        String author = planAuthor.getText().toString().trim();
        String classification = planClassification.getText().toString().trim().toUpperCase();
        settings.setDefaultAuthor(author);
        if (!classification.isEmpty()) settings.setDefaultClassification(classification);
        repository.updateMetadata(planName.getText().toString().trim(), author,
                classification.isEmpty() ? null : classification, null);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static double parse(EditText e) {
        return com.btlrckt.bottlerocket.util.Numbers.parseLenient(e.getText().toString());
    }

    private void updatePreview() {
        String formatted = com.btlrckt.bottlerocket.util.UnitConverter.format(
                SAMPLE_METERS, selectedUnit(), selectedPrecision());
        preview.setText(pluginContext.getString(R.string.settings_preview_fmt, formatted));
    }

    private void updateSeekLabels() {
        heatmapLabel.setText(pluginContext.getString(R.string.settings_heatmap_transparency_fmt,
                heatmapSeek.getProgress()));
        zoneLabel.setText(pluginContext.getString(R.string.settings_zone_transparency_fmt,
                zoneSeek.getProgress()));
    }

    private UnitSystem selectedUnit() {
        int id = unitGroup.getCheckedRadioButtonId();
        if (id == R.id.unit_feet) return UnitSystem.FEET;
        if (id == R.id.unit_km) return UnitSystem.KILOMETERS;
        return UnitSystem.METERS;
    }

    private int selectedPrecision() {
        int id = precisionGroup.getCheckedRadioButtonId();
        if (id == R.id.precision_0) return 0;
        if (id == R.id.precision_2) return 2;
        return 1;
    }

    private static int unitRadioId(UnitSystem u) {
        switch (u) {
            case FEET: return R.id.unit_feet;
            case KILOMETERS: return R.id.unit_km;
            default: return R.id.unit_meters;
        }
    }

    private static int precisionRadioId(int p) {
        if (p <= 0) return R.id.precision_0;
        if (p >= 2) return R.id.precision_2;
        return R.id.precision_1;
    }
}
