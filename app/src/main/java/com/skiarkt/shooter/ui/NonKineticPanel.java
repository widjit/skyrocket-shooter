package com.skiarkt.shooter.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.analysis.CoverageEngine;
import com.skiarkt.shooter.analysis.RfPropagation;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.DroneClassPreset;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PlanColors;
import com.skiarkt.shooter.model.RfBand;
import com.skiarkt.shooter.model.RfHeatmapMode;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;

/** Non-kinetic section: RF jammers with terrain-aware received-power heat maps. */
public class NonKineticPanel extends PointItemPanel<NonKineticEffector> {

    private EditText powerInput;
    private CheckBox globalThreshold;
    private EditText thresholdInput;
    private CheckBox globalFreq;
    private EditText freqInput;
    private final int[] effColor = new int[1];
    private final int[] margColor = new int[1];
    private final int[] insufColor = new int[1];
    private SeekBar opacitySeek;
    // R1 multi-band editor state
    private final List<RfBand> workBands = new ArrayList<>();
    private final List<BandRow> bandRows = new ArrayList<>();
    private LinearLayout bandsBox;
    private RfHeatmapMode selMode = RfHeatmapMode.UNION;
    private List<CheckBox> sensorBoxes;

    /** Widgets of one band in the editor. */
    private static final class BandRow {
        EditText label;
        EditText freq;
        EditText eirp;
        EditText thr;
        DistanceField range;
        CheckBox enabled;
    }

    public NonKineticPanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, OverlayManager overlayManager, Runnable onBack) {
        super(pluginContext, mapView, settings, repository, overlayManager, onBack);
    }

    @Override
    ItemKind kind() {
        return ItemKind.NON_KINETIC_EFFECTOR;
    }

    @Override
    int sectionTitleRes() {
        return R.string.section_non_kinetic;
    }

    @Override
    int newButtonRes() {
        return R.string.nk_new;
    }

    @Override
    int listHeaderRes() {
        return R.string.nk_list_header;
    }

    @Override
    int listEmptyRes() {
        return R.string.nk_list_empty;
    }

    @Override
    int editTitleRes(boolean isNew) {
        return isNew ? R.string.nk_edit_title_new : R.string.nk_edit_title;
    }

    @Override
    NonKineticEffector createItem(GeoPos position) {
        NonKineticEffector n = new NonKineticEffector();
        n.position = position;
        n.color = settings.getColor(SettingsManager.ColorKey.NKE_HEATMAP);
        n.heatmapColor = n.color;
        n.label = "RF Effector " + (repository.getItems(ItemKind.NON_KINETIC_EFFECTOR).size() + 1);
        n.rfThresholdDbm = settings.getRfThresholdDbm();
        n.frequencyMhz = settings.getRfFrequencyMhz();
        return n;
    }

    @Override
    NonKineticEffector copyOf(NonKineticEffector item) {
        return item.copy();
    }

    @Override
    String listText(NonKineticEffector n) {
        return ctx.getString(R.string.nk_list_item_fmt, n.label, n.eirpDbm,
                settings.formatDistance(n.maxEngagementDistMeters));
    }

    @Override
    void addListExtras(LinearLayout container) {
        int pct = Math.round(settings.getHeatmapOpacity() * 100f);
        final TextView l = text(ctx.getString(R.string.nk_global_opacity_fmt, pct), 0xFFB0BEC5);
        l.setPadding(0, dp(6), 0, 0);
        container.addView(l, matchWrap());
        SeekBar s = new SeekBar(ctx);
        s.setMax(100);
        s.setProgress(pct);
        s.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                l.setText(ctx.getString(R.string.nk_global_opacity_fmt, p));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                settings.setHeatmapOpacity(seekBar.getProgress() / 100f); // re-renders via pref listener
            }
        });
        container.addView(s, matchWrap());
    }

    @Override
    void buildSpecificFields(LinearLayout form, NonKineticEffector item) {
        powerInput = numberField(form, ctx.getString(R.string.nk_power), item.eirpDbm, true);

        globalThreshold = checkBox(form, ctx.getString(R.string.nk_use_global_threshold_fmt,
                settings.getRfThresholdDbm()), item.useGlobalThreshold);
        thresholdInput = numberField(form, ctx.getString(R.string.nk_threshold), item.rfThresholdDbm, true);
        bindEnabled(globalThreshold, thresholdInput);

        globalFreq = checkBox(form, ctx.getString(R.string.nk_use_global_freq_fmt,
                settings.getRfFrequencyMhz()), item.useGlobalFrequency);
        freqInput = numberField(form, ctx.getString(R.string.nk_freq), item.frequencyMhz, false);
        bindEnabled(globalFreq, freqInput);

        effColor[0] = orDefault(item.heatmapColor, PlanColors.RF_EFFECTIVE);
        margColor[0] = orDefault(item.marginalColor, PlanColors.RF_MARGINAL);
        insufColor[0] = orDefault(item.insufficientColor, PlanColors.RF_INSUFFICIENT);
        labelledSwatches(form, R.string.nk_color_effective, effColor);
        labelledSwatches(form, R.string.nk_color_marginal, margColor);
        labelledSwatches(form, R.string.nk_color_insufficient, insufColor);
        opacitySeek = percentSeek(form, R.string.nk_opacity_fmt, Math.round(item.getOpacity() * 100f));

        // ---- R1 multi-band
        form.addView(heading(ctx.getString(R.string.nk_bands_header)));
        form.addView(text(ctx.getString(R.string.nk_bands_hint), 0xFF78909C), matchWrap());
        selMode = item.getHeatmapMode();
        final Button modeBtn = button(selMode.displayName);
        modeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selMode = selMode == RfHeatmapMode.UNION ? RfHeatmapMode.WEAKEST : RfHeatmapMode.UNION;
                modeBtn.setText(selMode.displayName);
            }
        });
        form.addView(modeBtn, matchWrap());
        workBands.clear();
        item.ensureLists();
        for (RfBand b : item.bands) if (b != null) workBands.add(b.copy());
        bandsBox = vertical();
        bandsBox.setPadding(0, 0, 0, 0);
        form.addView(bandsBox, matchWrap());
        LinearLayout bandBtns = horizontal();
        Button addBand = button(ctx.getString(R.string.nk_band_add));
        addBand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncBands();
                double eirp = parseNumber(powerInput.getText().toString());
                RfBand b = new RfBand();
                if (!Double.isNaN(eirp)) b.eirpDbm = eirp;
                b.thresholdDbm = currentThreshold();
                workBands.add(b);
                rebuildBands();
            }
        });
        Button preset = button(ctx.getString(R.string.nk_band_preset));
        preset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePreset();
            }
        });
        Button clear = button(ctx.getString(R.string.nk_band_clear));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                workBands.clear();
                rebuildBands();
            }
        });
        bandBtns.addView(addBand, weight());
        bandBtns.addView(preset, weight());
        bandBtns.addView(clear, weight());
        form.addView(bandBtns, matchWrap());
        rebuildBands();

        // ---- R0b linked sensors
        sensorBoxes = linkPicker(form, ctx.getString(R.string.link_sensors), new ItemKind[] {ItemKind.SENSOR},
                item.linkedSensorUids, item.uid);
    }

    private double currentThreshold() {
        if (globalThreshold.isChecked()) return settings.getRfThresholdDbm();
        double t = parseNumber(thresholdInput.getText().toString());
        return Double.isNaN(t) ? settings.getRfThresholdDbm() : t;
    }

    private void choosePreset() {
        final DroneClassPreset[] presets = DroneClassPreset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) names[i] = presets[i].displayName;
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(ctx.getString(R.string.nk_band_preset))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        double eirp = parseNumber(powerInput.getText().toString());
                        if (Double.isNaN(eirp)) eirp = editing != null ? editing.eirpDbm : 40.0;
                        workBands.clear();
                        workBands.addAll(presets[which].bands(eirp, currentThreshold(), 0.0));
                        rebuildBands();
                    }
                })
                .setNegativeButton(ctx.getString(R.string.pt_cancel), null)
                .show();
    }

    /** Copies parsable edits from the band widgets into {@link #workBands} (lenient). */
    private void syncBands() {
        for (int i = 0; i < bandRows.size() && i < workBands.size(); i++) {
            BandRow r = bandRows.get(i);
            RfBand b = workBands.get(i);
            b.label = r.label.getText().toString().trim();
            double v = parseNumber(r.freq.getText().toString());
            if (!Double.isNaN(v) && v > 0) b.frequencyMhz = v;
            v = parseNumber(r.eirp.getText().toString());
            if (!Double.isNaN(v)) b.eirpDbm = v;
            v = parseNumber(r.thr.getText().toString());
            if (!Double.isNaN(v)) b.thresholdDbm = v;
            v = r.range.read();
            if (!Double.isNaN(v) && v >= 0) b.maxRangeMeters = v;
            b.enabled = r.enabled.isChecked();
        }
    }

    private void rebuildBands() {
        if (bandsBox == null) return;
        bandsBox.removeAllViews();
        bandRows.clear();
        String unit = settings.getUnitSystem().abbreviation;
        if (workBands.isEmpty()) {
            bandsBox.addView(text(ctx.getString(R.string.nk_bands_none), 0xFFB0BEC5), matchWrap());
            return;
        }
        for (int i = 0; i < workBands.size(); i++) {
            final int idx = i;
            RfBand b = workBands.get(i);
            LinearLayout box = vertical();
            box.setBackgroundColor(0xFF263238);
            LinearLayout top = horizontal();
            BandRow r = new BandRow();
            r.enabled = new CheckBox(ctx);
            r.enabled.setText(ctx.getString(R.string.nk_band_title_fmt, i + 1, b.displayName()));
            r.enabled.setTextColor(0xFFFFFFFF);
            r.enabled.setChecked(b.enabled);
            top.addView(r.enabled, weight());
            Button del = button("\u2715");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    syncBands();
                    if (idx < workBands.size()) workBands.remove(idx);
                    rebuildBands();
                }
            });
            top.addView(del, wrap());
            box.addView(top, matchWrap());
            r.label = field(box, ctx.getString(R.string.nk_band_label), InputType.TYPE_CLASS_TEXT, b.label);
            r.freq = numberField(box, ctx.getString(R.string.nk_band_freq), b.frequencyMhz, false);
            r.eirp = numberField(box, ctx.getString(R.string.nk_band_eirp), b.eirpDbm, true);
            r.thr = numberField(box, ctx.getString(R.string.nk_band_thr), b.thresholdDbm, true);
            r.range = distanceField(box, ctx.getString(R.string.nk_band_range_fmt, unit), b.maxRangeMeters);
            bandRows.add(r);
            LinearLayout.LayoutParams lp = matchWrap();
            lp.topMargin = dp(4);
            bandsBox.addView(box, lp);
        }
    }

    private static int orDefault(int c, int def) {
        return (c == 0 ? def : c) | 0xFF000000;
    }

    private void labelledSwatches(LinearLayout form, int labelRes, int[] holder) {
        TextView l = text(ctx.getString(labelRes), 0xFFB0BEC5);
        l.setPadding(0, dp(6), 0, 0);
        form.addView(l, matchWrap());
        LinearLayout row = horizontal();
        swatchRow(row, holder, null);
        form.addView(row, matchWrap());
    }

    private CheckBox checkBox(LinearLayout form, String label, boolean checked) {
        CheckBox cb = new CheckBox(ctx);
        cb.setText(label);
        cb.setTextColor(0xFFFFFFFF);
        cb.setChecked(checked);
        form.addView(cb, matchWrap());
        return cb;
    }

    private static void bindEnabled(CheckBox useGlobal, final EditText override) {
        override.setEnabled(!useGlobal.isChecked());
        useGlobal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean isChecked) {
                override.setEnabled(!isChecked);
            }
        });
    }

    @Override
    String[] layerIds() {
        return new String[] {com.skiarkt.shooter.data.VisibilityStore.Layer.HEATMAP, com.skiarkt.shooter.data.VisibilityStore.Layer.RANGE_RING, com.skiarkt.shooter.data.VisibilityStore.Layer.MARKER};
    }

    @Override
    String readSpecificFields(NonKineticEffector item) {
        String err = null;
        double p = parseNumber(powerInput.getText().toString());
        if (Double.isNaN(p) || p < -30 || p > 100) err = ctx.getString(R.string.nk_power);
        else item.eirpDbm = p;

        item.useGlobalThreshold = globalThreshold.isChecked();
        double t = parseNumber(thresholdInput.getText().toString());
        if (Double.isNaN(t) || t < -150 || t > 50) {
            if (!item.useGlobalThreshold && err == null) err = ctx.getString(R.string.nk_threshold);
        } else {
            item.rfThresholdDbm = t;
        }

        item.useGlobalFrequency = globalFreq.isChecked();
        double f = parseNumber(freqInput.getText().toString());
        if (Double.isNaN(f) || f <= 0 || f > 100000) {
            if (!item.useGlobalFrequency && err == null) err = ctx.getString(R.string.nk_freq);
        } else {
            item.frequencyMhz = f;
        }

        item.heatmapColor = effColor[0];
        item.marginalColor = margColor[0];
        item.insufficientColor = insufColor[0];
        item.setOpacity(opacitySeek.getProgress() / 100f);

        syncBands();
        List<RfBand> bands = new ArrayList<>();
        for (int i = 0; i < workBands.size(); i++) {
            RfBand b = workBands.get(i);
            BandRow r = i < bandRows.size() ? bandRows.get(i) : null;
            if (r != null) {
                double fq = parseNumber(r.freq.getText().toString());
                double ei = parseNumber(r.eirp.getText().toString());
                double th = parseNumber(r.thr.getText().toString());
                double rg = r.range.read();
                if (Double.isNaN(fq) || fq <= 0 || fq > 100000 || Double.isNaN(ei) || ei < -30 || ei > 100
                        || Double.isNaN(th) || th < -150 || th > 50 || Double.isNaN(rg) || rg < 0) {
                    if (err == null) err = ctx.getString(R.string.nk_band_invalid_fmt, i + 1);
                    continue;
                }
            }
            if (b.isValid()) bands.add(b.copy());
        }
        item.bands = bands;
        item.heatmapMode = selMode;
        item.linkedSensorUids = checkedUids(sensorBoxes);
        return err;
    }

    @Override
    String statsText(NonKineticEffector item) {
        if (!item.isValid()) return null;
        CoverageEngine.HeatmapResult h = overlayManager.getHeatmap(item);
        if (h == null || h.raster == null) return ctx.getString(R.string.pt_stats_computing);
        double freq = item.effectiveFrequencyMhz(settings.getRfFrequencyMhz());
        double range = RfPropagation.rangeForThresholdM(item.eirpDbm, h.thresholdDbm, freq);
        String s = ctx.getString(R.string.nk_stats_fmt, h.raster.effectiveFraction * 100.0,
                h.raster.marginalFraction * 100.0, settings.formatDistance(range));
        if (item.isMultiBand()) {
            s += "\n" + ctx.getString(R.string.nk_bands_stats_fmt, item.enabledBands().size(), item.bands.size(),
                    item.getHeatmapMode().displayName);
        }
        if (h.los != null && !h.los.terrainAvailable) s += "\n" + ctx.getString(R.string.sn_stats_no_dted);
        return s;
    }
}
