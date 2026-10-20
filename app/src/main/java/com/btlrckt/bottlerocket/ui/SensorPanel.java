package com.btlrckt.bottlerocket.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.btlrckt.bottlerocket.R;
import com.btlrckt.bottlerocket.analysis.LosSweep;
import com.btlrckt.bottlerocket.data.PointDefenseRepository;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ItemKind;
import com.btlrckt.bottlerocket.model.SensorItem;
import com.btlrckt.bottlerocket.model.DayNight;
import com.btlrckt.bottlerocket.model.SensorPresetDef;
import com.btlrckt.bottlerocket.model.SensorType;
import com.btlrckt.bottlerocket.overlay.OverlayManager;
import com.btlrckt.bottlerocket.settings.SettingsManager;

import java.util.List;
import java.util.Locale;

/** Sensors section: radar / camera / EO-IR emplacements with DTED-masked coverage. */
public class SensorPanel extends PointItemPanel<SensorItem> {

    private RadioGroup typeGroup;
    private DistanceField rangeField;
    private EditText beamInput;
    private EditText azimuthInput;
    private SeekBar opacitySeek;
    private List<CheckBox> sensorBoxes;
    private TextView presetNote;
    /** R2 type-specific groups (shown / hidden by {@link #updateTypeVisibility()}). */
    private LinearLayout radarGroup;
    private LinearLayout imagingGroup;
    private LinearLayout laserGroup;
    private DistanceField minRangeField;
    private EditText elevMinInput;
    private EditText elevMaxInput;
    private RadioGroup dayNightGroup;
    private DistanceField lasingRangeField;
    private EditText prfInput;
    private EditText divergenceInput;

    public SensorPanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, OverlayManager overlayManager, Runnable onBack) {
        super(pluginContext, mapView, settings, repository, overlayManager, onBack);
    }

    @Override
    ItemKind kind() {
        return ItemKind.SENSOR;
    }

    @Override
    int sectionTitleRes() {
        return R.string.section_sensors;
    }

    @Override
    int newButtonRes() {
        return R.string.sn_new;
    }

    @Override
    int listHeaderRes() {
        return R.string.sn_list_header;
    }

    @Override
    int listEmptyRes() {
        return R.string.sn_list_empty;
    }

    @Override
    int editTitleRes(boolean isNew) {
        return isNew ? R.string.sn_edit_title_new : R.string.sn_edit_title;
    }

    @Override
    SensorItem createItem(GeoPos position) {
        SensorItem s = new SensorItem();
        s.position = position;
        s.color = settings.getColor(SettingsManager.ColorKey.SENSOR);
        s.label = "Sensor " + (repository.getItems(ItemKind.SENSOR).size() + 1);
        s.maxEngagementDistMeters = s.rangeMeters;
        return s;
    }

    @Override
    SensorItem copyOf(SensorItem item) {
        return item.copy();
    }

    @Override
    String listText(SensorItem s) {
        String beam = s.isOmnidirectional() ? "omni"
                : String.format(Locale.US, "%.0f\u00B0 @ %.0f\u00B0", s.beamWidthDeg, s.azimuthDeg);
        return ctx.getString(R.string.sn_list_item_fmt, s.label, s.type.displayName,
                settings.formatDistance(s.rangeMeters), beam);
    }

    @Override
    void buildSpecificFields(LinearLayout form, SensorItem item) {
        form.addView(text(ctx.getString(R.string.sn_type), 0xFFB0BEC5), matchWrap());
        typeGroup = new RadioGroup(ctx);
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        for (SensorType t : SensorType.values()) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(t.displayName);
            rb.setTextColor(0xFFFFFFFF);
            rb.setId(1000 + t.ordinal());
            typeGroup.addView(rb);
        }
        typeGroup.check(1000 + item.type.ordinal());
        form.addView(typeGroup, matchWrap());
        typeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                updateTypeVisibility();
            }
        });

        Button presetBtn = button(ctx.getString(R.string.sn_preset));
        presetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePresetModel();
            }
        });
        form.addView(presetBtn, matchWrap());
        presetNote = text("", 0xFF90A4AE);
        presetNote.setVisibility(View.GONE);
        form.addView(presetNote, matchWrap());

        String unit = settings.getUnitSystem().abbreviation;
        rangeField = distanceField(form, ctx.getString(R.string.sn_range_fmt, unit), item.rangeMeters);
        beamInput = numberField(form, ctx.getString(R.string.sn_beam), item.beamWidthDeg, false);
        azimuthInput = numberField(form, ctx.getString(R.string.sn_azimuth), item.azimuthDeg, false);
        opacitySeek = percentSeek(form, R.string.pt_opacity_fmt, Math.round(item.opacity * 100f));
        buildTypeFields(form, item, unit);
        sensorBoxes = linkPicker(form, ctx.getString(R.string.link_sensors), new ItemKind[] {ItemKind.SENSOR},
                item.linkedSensorUids, item.uid);
    }

    /** R2: radar (min range, elevation), imaging (day / night) and laser designator groups. */
    private void buildTypeFields(LinearLayout form, SensorItem item, String unit) {
        radarGroup = group(form);
        minRangeField = distanceField(radarGroup, ctx.getString(R.string.sn_min_range_fmt, unit), item.minRangeMeters);
        elevMinInput = field(radarGroup, ctx.getString(R.string.sn_elev_min),
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, deg(item.elevationMinDeg));
        elevMaxInput = field(radarGroup, ctx.getString(R.string.sn_elev_max),
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, deg(item.elevationMaxDeg));

        imagingGroup = group(form);
        imagingGroup.addView(text(ctx.getString(R.string.sn_day_night), 0xFFB0BEC5), matchWrap());
        dayNightGroup = new RadioGroup(ctx);
        dayNightGroup.setOrientation(RadioGroup.HORIZONTAL);
        for (DayNight d : DayNight.values()) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(d.displayName);
            rb.setTextColor(0xFFFFFFFF);
            rb.setId(2000 + d.ordinal());
            dayNightGroup.addView(rb);
        }
        dayNightGroup.check(2000 + (item.dayNight == null ? DayNight.DAY_NIGHT : item.dayNight).ordinal());
        imagingGroup.addView(dayNightGroup, matchWrap());

        laserGroup = group(form);
        lasingRangeField = distanceField(laserGroup, ctx.getString(R.string.sn_lasing_range_fmt, unit),
                item.maxLasingRangeMeters);
        prfInput = field(laserGroup, ctx.getString(R.string.sn_prf), android.text.InputType.TYPE_CLASS_NUMBER,
                String.valueOf(item.laserPrfCode));
        divergenceInput = field(laserGroup, ctx.getString(R.string.sn_divergence),
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL,
                String.format(Locale.US, "%.2f", item.beamDivergenceMrad));
        updateTypeVisibility();
    }

    private LinearLayout group(LinearLayout form) {
        LinearLayout g = new LinearLayout(ctx);
        g.setOrientation(LinearLayout.VERTICAL);
        form.addView(g, matchWrap());
        return g;
    }

    private static String deg(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.US, "%.1f", v);
    }

    private SensorType selectedType() {
        int checked = typeGroup == null ? -1 : typeGroup.getCheckedRadioButtonId() - 1000;
        return checked >= 0 && checked < SensorType.values().length ? SensorType.values()[checked] : SensorType.RADAR;
    }

    /** Shows only the fields relevant to the selected sensor type. */
    void updateTypeVisibility() {
        if (radarGroup == null) return;
        SensorType t = selectedType();
        radarGroup.setVisibility(t == SensorType.RADAR ? View.VISIBLE : View.GONE);
        imagingGroup.setVisibility(t.isImaging() ? View.VISIBLE : View.GONE);
        laserGroup.setVisibility(t == SensorType.LASER_DESIGNATOR ? View.VISIBLE : View.GONE);
    }

    /** Step 1: radar model (R10 merged catalog: built-ins with local edits, then custom presets). */
    private void choosePresetModel() {
        List<SensorPresetDef> list = repository.getPresetStore().getMergedList();
        final SensorPresetDef[] presets = list.toArray(new SensorPresetDef[0]);
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = presets[i].displayName + (presets[i].description == null || presets[i].description.isEmpty()
                    ? "" : " \u2014 " + presets[i].description);
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(ctx.getString(R.string.sn_preset_title))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        choosePresetTarget(presets[which]);
                    }
                })
                .setNegativeButton(ctx.getString(R.string.pt_cancel), null)
                .show();
    }

    /** Step 2: target class (sets detection range). */
    private void choosePresetTarget(final SensorPresetDef p) {
        String[] names = new String[p.targetClassCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = ctx.getString(R.string.sn_preset_target_item_fmt, p.targetClass(i),
                    settings.formatDistance(p.rangeMeters(i)));
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(ctx.getString(R.string.sn_preset_target_fmt, p.displayName))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        choosePresetPanels(p, which);
                    }
                })
                .setNegativeButton(ctx.getString(R.string.pt_cancel), null)
                .show();
    }

    /** Step 3: panel count (sets horizontal FOV; 4 panels = 360 omni). */
    private void choosePresetPanels(final SensorPresetDef p, final int targetIdx) {
        String[] names = new String[p.panelsFor360()];
        for (int i = 0; i < names.length; i++) {
            names[i] = ctx.getString(R.string.sn_preset_panel_item_fmt, i + 1, p.beamWidthDeg(i + 1));
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(ctx.getString(R.string.sn_preset_panels_fmt, p.displayName))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        applyPreset(p, targetIdx, which + 1);
                    }
                })
                .setNegativeButton(ctx.getString(R.string.pt_cancel), null)
                .show();
    }

    private void applyPreset(SensorPresetDef p, int targetIdx, int panels) {
        SensorItem tmp = new SensorItem();
        p.applyTo(tmp, targetIdx, panels);
        typeGroup.check(1000 + tmp.type.ordinal());
        rangeField.set(tmp.rangeMeters);
        beamInput.setText(String.format(Locale.US, "%.1f", tmp.beamWidthDeg));
        minRangeField.set(tmp.minRangeMeters);
        elevMinInput.setText(deg(tmp.elevationMinDeg));
        elevMaxInput.setText(deg(tmp.elevationMaxDeg));
        updateTypeVisibility();
        if (labelField() != null) {
            String cur = labelField().getText().toString().trim();
            if (cur.isEmpty() || cur.matches("(Sensor|RPS-42|nMHR|SUADS|B202|"
                    + java.util.regex.Pattern.quote(p.shortName()) + ") \\d+")) {
                labelField().setText(p.shortName() + " " + (repository.getItems(ItemKind.SENSOR).size() + 1));
            }
        }
        String min = p.minRangeMeters > 0 ? settings.formatDistance(p.minRangeMeters)
                : ctx.getString(R.string.sn_preset_min_na);
        String note = ctx.getString(R.string.sn_preset_note_fmt, p.elevationText(), min);
        presetNote.setText(ctx.getString(R.string.sn_preset_applied_fmt, p.displayName,
                p.targetClass(targetIdx), tmp.beamWidthDeg, note));
        presetNote.setVisibility(View.VISIBLE);
    }

    @Override
    String[] layerIds() {
        return new String[] {com.btlrckt.bottlerocket.data.VisibilityStore.Layer.COVERAGE, com.btlrckt.bottlerocket.data.VisibilityStore.Layer.TERRAIN_MASK, com.btlrckt.bottlerocket.data.VisibilityStore.Layer.ENGAGE_RING, com.btlrckt.bottlerocket.data.VisibilityStore.Layer.MIN_RANGE_RING, com.btlrckt.bottlerocket.data.VisibilityStore.Layer.MARKER};
    }

    @Override
    String readSpecificFields(SensorItem item) {
        String err = null;
        int checked = typeGroup.getCheckedRadioButtonId() - 1000;
        if (checked >= 0 && checked < SensorType.values().length) item.type = SensorType.values()[checked];
        double range = rangeField.read();
        if (Double.isNaN(range) || range <= 0) {
            err = ctx.getString(R.string.sn_range_fmt, settings.getUnitSystem().abbreviation);
        } else {
            item.rangeMeters = range;
        }
        double beam = parseNumber(beamInput.getText().toString());
        if (Double.isNaN(beam) || beam <= 0 || beam > 360) {
            if (err == null) err = ctx.getString(R.string.sn_beam);
        } else {
            item.beamWidthDeg = beam;
        }
        double az = parseNumber(azimuthInput.getText().toString());
        if (Double.isNaN(az)) {
            if (err == null) err = ctx.getString(R.string.sn_azimuth);
        } else {
            item.azimuthDeg = ((az % 360.0) + 360.0) % 360.0;
        }
        if (item.type == SensorType.RADAR) {
            double min = minRangeField.read();
            if (Double.isNaN(min) || min < 0 || min >= item.rangeMeters) {
                if (err == null) err = ctx.getString(R.string.sn_min_range_fmt, settings.getUnitSystem().abbreviation);
            } else {
                item.minRangeMeters = min;
            }
            double eMin = parseOptional(elevMinInput), eMax = parseOptional(elevMaxInput);
            if (!Double.isNaN(eMin) && !Double.isNaN(eMax) && eMin > eMax) {
                if (err == null) err = ctx.getString(R.string.sn_elev_min);
            } else {
                item.elevationMinDeg = eMin;
                item.elevationMaxDeg = eMax;
            }
        } else {
            item.minRangeMeters = 0;
        }
        if (item.type.isImaging()) {
            int dn = dayNightGroup.getCheckedRadioButtonId() - 2000;
            if (dn >= 0 && dn < DayNight.values().length) item.dayNight = DayNight.values()[dn];
        }
        if (item.type == SensorType.LASER_DESIGNATOR) {
            double lr = lasingRangeField.read();
            if (Double.isNaN(lr) || lr < 0) {
                if (err == null) err = ctx.getString(R.string.sn_lasing_range_fmt, settings.getUnitSystem().abbreviation);
            } else {
                item.maxLasingRangeMeters = lr;
            }
            double prf = parseNumber(prfInput.getText().toString());
            if (Double.isNaN(prf) || prf < 0 || prf != Math.rint(prf)) {
                if (err == null) err = ctx.getString(R.string.sn_prf);
            } else {
                item.laserPrfCode = (int) prf;
            }
            double div = parseNumber(divergenceInput.getText().toString());
            if (Double.isNaN(div) || div < 0) {
                if (err == null) err = ctx.getString(R.string.sn_divergence);
            } else {
                item.beamDivergenceMrad = div;
            }
        }
        item.opacity = opacitySeek.getProgress() / 100f;
        item.linkedSensorUids = checkedUids(sensorBoxes);
        return err;
    }

    /** Blank = NaN (unknown). */
    private static double parseOptional(EditText e) {
        String t = e.getText().toString().trim();
        return t.isEmpty() ? Double.NaN : parseNumber(t);
    }

    @Override
    String statsText(SensorItem item) {
        if (!item.isValid()) return null;
        LosSweep.Result r = overlayManager.getSensorMask(item);
        if (r == null) return ctx.getString(R.string.pt_stats_computing);
        if (!r.terrainAvailable) return ctx.getString(R.string.sn_stats_no_dted);
        return ctx.getString(R.string.sn_stats_fmt, settings.formatDistance(settings.getLosTargetAglMeters()),
                r.visibleFraction() * 100.0, r.angularStepDeg, settings.formatDistance(r.rangeStepM));
    }
}
