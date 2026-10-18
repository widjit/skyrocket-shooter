package com.skiarkt.shooter.ui;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;

import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.analysis.LosSweep;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.SensorItem;
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

        String unit = settings.getUnitSystem().abbreviation;
        rangeField = distanceField(form, ctx.getString(R.string.sn_range_fmt, unit), item.rangeMeters);
        beamInput = numberField(form, ctx.getString(R.string.sn_beam), item.beamWidthDeg, false);
        azimuthInput = numberField(form, ctx.getString(R.string.sn_azimuth), item.azimuthDeg, false);
        opacitySeek = percentSeek(form, R.string.pt_opacity_fmt, Math.round(item.opacity * 100f));
        sensorBoxes = linkPicker(form, ctx.getString(R.string.link_sensors), new ItemKind[] {ItemKind.SENSOR},
                item.linkedSensorUids, item.uid);
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
