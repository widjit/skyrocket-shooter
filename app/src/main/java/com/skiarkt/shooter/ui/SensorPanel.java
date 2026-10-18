package com.skiarkt.shooter.ui;

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
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.analysis.LosSweep;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.model.SensorPreset;
import com.skiarkt.shooter.model.SensorType;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;

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
        sensorBoxes = linkPicker(form, ctx.getString(R.string.link_sensors), new ItemKind[] {ItemKind.SENSOR},
                item.linkedSensorUids, item.uid);
    }

    /** Step 1: radar model. */
    private void choosePresetModel() {
        final SensorPreset[] presets = SensorPreset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            names[i] = presets[i].displayName + " \u2014 " + presets[i].description;
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
    private void choosePresetTarget(final SensorPreset p) {
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
    private void choosePresetPanels(final SensorPreset p, final int targetIdx) {
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

    private void applyPreset(SensorPreset p, int targetIdx, int panels) {
        SensorItem tmp = new SensorItem();
        p.applyTo(tmp, targetIdx, panels);
        typeGroup.check(1000 + tmp.type.ordinal());
        rangeField.set(tmp.rangeMeters);
        beamInput.setText(String.format(Locale.US, "%.1f", tmp.beamWidthDeg));
        if (labelField() != null) {
            String cur = labelField().getText().toString().trim();
            if (cur.isEmpty() || cur.matches("(Sensor|RPS-42|nMHR|SUADS|B202) \\d+")) {
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
        item.opacity = opacitySeek.getProgress() / 100f;
        item.linkedSensorUids = checkedUids(sensorBoxes);
        return err;
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
