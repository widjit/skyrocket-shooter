package com.skiarkt.shooter.ui;

import android.content.Context;
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
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PlanColors;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;

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
        if (h.los != null && !h.los.terrainAvailable) s += "\n" + ctx.getString(R.string.sn_stats_no_dted);
        return s;
    }
}
