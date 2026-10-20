package com.btlrckt.bottlerocket.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.btlrckt.bottlerocket.R;
import com.btlrckt.bottlerocket.data.CustomPresetStore;
import com.btlrckt.bottlerocket.model.SensorPresetDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R10 "Sensor presets" section of Settings: merged catalog list with Edit / Reset (built-in) or
 * Edit / Delete (custom) per row, plus "Add custom preset". Edits apply immediately to the local
 * {@link CustomPresetStore}; the sensor preset picker reads the merged list.
 */
final class SensorPresetsUi {
    /** Target class rows offered in the edit dialog. */
    static final int MAX_TARGET_CLASSES = 5;

    private final Context ctx;
    private final CustomPresetStore store;
    private final LinearLayout container;

    SensorPresetsUi(Context pluginContext, CustomPresetStore store, LinearLayout container) {
        this.ctx = pluginContext;
        this.store = store;
        this.container = container;
    }

    void populate() {
        if (container == null) return;
        container.removeAllViews();
        for (final SensorPresetDef d : store.getMergedList()) {
            final boolean builtIn = d.isBuiltIn();
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView t = new TextView(ctx);
            String suffix = builtIn ? (store.isOverridden(d.id) ? ctx.getString(R.string.sp_edited_suffix) : "")
                    : ctx.getString(R.string.sp_custom_suffix);
            t.setText(d.displayName + suffix);
            t.setTextColor(0xFFFFFFFF);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button edit = button(ctx.getString(R.string.sp_edit));
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditor(d, false);
                }
            });
            row.addView(edit);
            Button second = button(ctx.getString(builtIn ? R.string.sp_reset : R.string.sp_delete));
            second.setEnabled(!builtIn || store.isOverridden(d.id));
            second.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (builtIn) store.resetToDefault(d.id);
                    else store.removeCustom(d.id);
                    populate();
                }
            });
            row.addView(second);
            container.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        Button add = button(ctx.getString(R.string.sp_add));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorPresetDef d = new SensorPresetDef();
                d.displayName = "";
                d.shortName = "";
                d.description = "";
                showEditor(d, true);
            }
        });
        container.addView(add);
    }

    private void showEditor(final SensorPresetDef src, final boolean isNew) {
        MapView mv = MapView.getMapView();
        Context host = mv != null ? mv.getContext() : ctx;
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * ctx.getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, pad / 2);
        final EditText name = field(form, R.string.sp_name, InputType.TYPE_CLASS_TEXT, src.displayName);
        final EditText shortName = field(form, R.string.sp_short, InputType.TYPE_CLASS_TEXT, src.shortName);
        final EditText desc = field(form, R.string.sp_desc, InputType.TYPE_CLASS_TEXT, src.description);
        int dec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        int signed = dec | InputType.TYPE_NUMBER_FLAG_SIGNED;
        final EditText az = field(form, R.string.sp_panel_az, dec, num(src.panelAzimuthDeg));
        final EditText min = field(form, R.string.sp_min_range, dec, num(src.minRangeMeters));
        final EditText eMin = field(form, R.string.sp_elev_min, signed, num(src.elevationMinDeg));
        final EditText eMax = field(form, R.string.sp_elev_max, signed, num(src.elevationMaxDeg));
        final List<EditText> classes = new ArrayList<>();
        final List<EditText> ranges = new ArrayList<>();
        int n = Math.max(MAX_TARGET_CLASSES, src.targetClassCount());
        for (int i = 0; i < n; i++) {
            boolean has = i < src.targetClassCount();
            classes.add(field(form, ctx.getString(R.string.sp_target_fmt, i + 1), InputType.TYPE_CLASS_TEXT,
                    has ? src.targetClass(i) : ""));
            ranges.add(field(form, ctx.getString(R.string.sp_range_fmt, i + 1), dec,
                    has ? num(src.rangeMeters(i)) : ""));
        }
        ScrollView sv = new ScrollView(ctx);
        sv.addView(form);
        new AlertDialog.Builder(host)
                .setTitle(isNew ? ctx.getString(R.string.sp_add_title)
                        : ctx.getString(R.string.sp_edit_title_fmt, src.displayName))
                .setView(sv)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int which) {
                        SensorPresetDef d = src.copy();
                        d.displayName = name.getText().toString().trim();
                        d.shortName = shortName.getText().toString().trim();
                        d.description = desc.getText().toString().trim();
                        d.panelAzimuthDeg = parse(az);
                        double m = parse(min);
                        d.minRangeMeters = Double.isNaN(m) ? 0 : m;
                        d.elevationMinDeg = parse(eMin);
                        d.elevationMaxDeg = parse(eMax);
                        List<String> cls = new ArrayList<>();
                        List<Double> rs = new ArrayList<>();
                        for (int i = 0; i < classes.size(); i++) {
                            String c = classes.get(i).getText().toString().trim();
                            double r = parse(ranges.get(i));
                            if (c.isEmpty() && Double.isNaN(r)) continue;
                            cls.add(c.isEmpty() ? "Target " + (i + 1) : c);
                            rs.add(r);
                        }
                        d.targetClasses = cls.toArray(new String[0]);
                        d.rangesMeters = new double[rs.size()];
                        for (int i = 0; i < rs.size(); i++) d.rangesMeters[i] = rs.get(i);
                        boolean ok = isNew ? store.addCustom(d) != null : store.saveOverride(d.id, d);
                        Toast.makeText(ctx, ctx.getString(ok ? R.string.sp_saved : R.string.sp_invalid),
                                Toast.LENGTH_LONG).show();
                        populate();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private EditText field(LinearLayout form, int labelRes, int type, String value) {
        return field(form, ctx.getString(labelRes), type, value);
    }

    private EditText field(LinearLayout form, String label, int type, String value) {
        TextView l = new TextView(ctx);
        l.setText(label);
        l.setTextColor(0xFFB0BEC5);
        form.addView(l);
        EditText e = new EditText(ctx);
        e.setInputType(type);
        e.setTextColor(0xFFFFFFFF);
        e.setText(value == null ? "" : value);
        form.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private static String num(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.US, "%.1f", v);
    }

    /** Blank / invalid = NaN. */
    static double parse(EditText e) {
        return com.btlrckt.bottlerocket.util.Numbers.parseLenient(e.getText().toString());
    }
}
