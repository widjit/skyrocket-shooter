package com.skiarkt.shooter.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.PointItem;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.tools.PointPlacementTool;

import java.util.List;
import java.util.Locale;

/**
 * Shared side-panel flow for single-point emplacements (sensors, NK effectors): CRUD list,
 * tap-to-place placement mode ({@link PointPlacementTool}) and a properties form with the
 * common fields (label, editable lat/lon, unit-aware AGL + max engagement distance, color,
 * remarks) plus subclass-specific fields. Forms are built in code. UI thread only.
 *
 * <p>Unit-aware fields remember their pre-filled text: if the user does not touch a field,
 * the exact stored meter value is kept instead of the rounded display value.
 */
abstract class PointItemPanel<T extends PointItem> {

    protected final Context ctx;
    protected final MapView mapView;
    protected final SettingsManager settings;
    protected final PointDefenseRepository repository;
    protected final OverlayManager overlayManager;
    private final Runnable onBack;

    private FrameLayout root;
    private PointPlacementTool placementTool;
    private LinearLayout listContainer;
    private TextView listEmpty;
    private View placeBar;
    private View listSection;
    private boolean listShowing;

    // edit state
    protected T editing;
    private boolean editingIsNew;
    private EditText labelInput;
    private EditText latInput;
    private EditText lonInput;
    private String latPrefill;
    private String lonPrefill;
    private DistanceField aglField;
    private DistanceField engageField;
    private EditText remarksInput;
    private TextView statsText;
    protected int selectedColor;

    PointItemPanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, OverlayManager overlayManager, Runnable onBack) {
        this.ctx = pluginContext;
        this.mapView = mapView;
        this.settings = settings;
        this.repository = repository;
        this.overlayManager = overlayManager;
        this.onBack = onBack;
    }

    // ------------------------------------------------------------- subclass hooks

    abstract ItemKind kind();

    abstract int sectionTitleRes();

    abstract int newButtonRes();

    abstract int listHeaderRes();

    abstract int listEmptyRes();

    abstract int editTitleRes(boolean isNew);

    /** New item at the tapped position with defaults from Settings. */
    abstract T createItem(GeoPos position);

    abstract T copyOf(T item);

    abstract String listText(T item);

    /** Extra controls above the list (e.g. global heat map opacity). */
    void addListExtras(LinearLayout container) {
    }

    abstract void buildSpecificFields(LinearLayout form, T item);

    /** Reads subclass fields into {@code item}; returns an error description or null. */
    abstract String readSpecificFields(T item);

    /** Analysis summary for the editor (null = none). */
    abstract String statsText(T item);

    // ------------------------------------------------------------------ lifecycle

    public View createView() {
        cancelPlacement();
        editing = null;
        root = new FrameLayout(ctx);
        showList();
        return root;
    }

    public void refresh() {
        if (isBusy() || !listShowing) return;
        populateList();
    }

    public boolean isBusy() {
        return editing != null || (placementTool != null && placementTool.isActive());
    }

    public void onHidden() {
        cancelPlacement();
    }

    public void dispose() {
        cancelPlacement();
    }

    // ----------------------------------------------------------------------- LIST

    private void showList() {
        editing = null;
        listShowing = true;
        LinearLayout v = vertical();
        Button back = button(ctx.getString(R.string.btn_back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelPlacement();
                onBack.run();
            }
        });
        v.addView(back, wrap());
        v.addView(heading(ctx.getString(sectionTitleRes())));

        LinearLayout bar = vertical();
        bar.setBackgroundColor(0xFF263238);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.addView(text(ctx.getString(R.string.pt_place_hint), 0xFFFFFFFF));
        Button cancelPlace = button(ctx.getString(R.string.pt_cancel_place));
        cancelPlace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelPlacement();
            }
        });
        bar.addView(cancelPlace, matchWrap());
        bar.setVisibility(View.GONE);
        placeBar = bar;
        v.addView(bar, matchWrap());

        LinearLayout section = vertical();
        listSection = section;
        Button create = button(ctx.getString(newButtonRes()));
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPlacement(null);
            }
        });
        section.addView(create, matchWrap());
        addListExtras(section);
        section.addView(heading(ctx.getString(listHeaderRes())));
        listEmpty = text(ctx.getString(listEmptyRes()), 0xFFB0BEC5);
        section.addView(listEmpty);
        listContainer = vertical();
        section.addView(listContainer, matchWrap());
        v.addView(section, matchWrap());

        populateList();
        setRoot(scroll(v));
    }

    private void populateList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        List<T> items = repository.getItems(kind());
        listEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (final T item : items) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View swatch = new View(ctx);
            swatch.setBackground(swatchDrawable(item.color | 0xFF000000, false));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(20), dp(20));
            slp.rightMargin = dp(6);
            row.addView(swatch, slp);
            TextView t = text(listText(item), 0xFFFFFFFF);
            t.setPadding(0, dp(8), 0, dp(8));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEdit(copyOf(item), false);
                }
            });
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button del = button("\u2715");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete(item.uid, item.label, false);
                }
            });
            row.addView(del, wrap());
            listContainer.addView(row, matchWrap());
        }
    }

    // ------------------------------------------------------------------ PLACEMENT

    /** @param reposition item whose position is replaced (editor re-place), or null for a new item. */
    private void startPlacement(final T reposition) {
        if (placementTool == null) placementTool = new PointPlacementTool(mapView);
        if (reposition != null) showList();
        boolean ok = placementTool.begin(new PointPlacementTool.Listener() {
            @Override
            public void onPlaced(GeoPos position) {
                if (reposition != null) {
                    reposition.position = position;
                    showEdit(reposition, editingIsNew);
                } else {
                    showEdit(createItem(position), true);
                }
            }

            @Override
            public void onPlacementCancelled() {
                if (reposition != null) showEdit(reposition, editingIsNew);
                else exitPlaceBar();
            }
        });
        if (!ok) {
            toast(ctx.getString(R.string.pt_place_unavailable));
            if (reposition != null) showEdit(reposition, editingIsNew);
            return;
        }
        if (placeBar != null) placeBar.setVisibility(View.VISIBLE);
        if (listSection != null) listSection.setVisibility(View.GONE);
    }

    private void exitPlaceBar() {
        if (placeBar != null) placeBar.setVisibility(View.GONE);
        if (listSection != null) listSection.setVisibility(View.VISIBLE);
    }

    private void cancelPlacement() {
        if (placementTool != null && placementTool.isActive()) placementTool.cancel();
    }

    // ---------------------------------------------------------------------- EDIT

    protected void showEdit(final T item, boolean isNew) {
        editing = item;
        editingIsNew = isNew;
        listShowing = false;
        listContainer = null;
        String unit = settings.getUnitSystem().abbreviation;
        LinearLayout form = vertical();
        form.addView(heading(ctx.getString(editTitleRes(isNew))));

        labelInput = field(form, ctx.getString(R.string.pt_label), InputType.TYPE_CLASS_TEXT, item.label);
        latPrefill = String.format(Locale.US, "%.6f", item.position.lat);
        lonPrefill = String.format(Locale.US, "%.6f", item.position.lon);
        int signedDecimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        latInput = field(form, ctx.getString(R.string.pt_lat), signedDecimal, latPrefill);
        lonInput = field(form, ctx.getString(R.string.pt_lon), signedDecimal, lonPrefill);
        aglField = distanceField(form, ctx.getString(R.string.pt_agl_fmt, unit), item.aglMeters);
        engageField = distanceField(form, ctx.getString(R.string.pt_max_engage_fmt, unit),
                item.maxEngagementDistMeters);
        colorPicker(form, ctx.getString(R.string.pt_color), item.color);

        buildSpecificFields(form, item);

        statsText = text("", 0xFFFFC107);
        statsText.setPadding(0, dp(6), 0, dp(2));
        statsText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateStats();
            }
        });
        form.addView(statsText, matchWrap());
        updateStats();

        remarksInput = field(form, ctx.getString(R.string.pt_remarks),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, item.remarks);

        LinearLayout row1 = horizontal();
        Button save = button(ctx.getString(R.string.pt_save));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                T saved = save();
                if (saved != null) toast(ctx.getString(R.string.pt_saved_fmt, saved.label));
            }
        });
        Button cancel = button(ctx.getString(R.string.pt_cancel));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showList();
            }
        });
        row1.addView(save, weight());
        row1.addView(cancel, weight());
        form.addView(row1, matchWrap());

        LinearLayout row2 = horizontal();
        Button move = button(ctx.getString(R.string.pt_move));
        move.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readForm(item); // keep typed values while re-placing
                startPlacement(item);
            }
        });
        Button share = button(ctx.getString(R.string.pt_share));
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                T saved = save();
                if (saved == null) return;
                BasePlanItem stored = repository.find(saved.uid);
                boolean ok = stored != null && repository.share(stored);
                toast(ok ? ctx.getString(R.string.pt_shared_fmt, saved.label) : ctx.getString(R.string.pt_share_failed));
            }
        });
        Button delete = button(ctx.getString(R.string.pt_delete));
        delete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete(item.uid, labelInput.getText().toString().trim(), true);
            }
        });
        row2.addView(move, weight());
        row2.addView(share, weight());
        row2.addView(delete, weight());
        form.addView(row2, matchWrap());

        setRoot(scroll(form));
    }

    /** Refreshes the analysis summary (results are cached / computed in the background). */
    protected void updateStats() {
        if (statsText == null || editing == null) return;
        String s = statsText(editing);
        statsText.setText(s == null ? "" : s);
        statsText.setVisibility(s == null ? View.GONE : View.VISIBLE);
    }

    /** Copies form values into {@code item}; returns an error description or null. */
    private String readForm(T item) {
        String label = labelInput.getText().toString().trim();
        if (!label.isEmpty()) item.label = label;
        item.remarks = remarksInput.getText().toString();
        item.color = selectedColor;
        String err = null;
        String lat = latInput.getText().toString().trim();
        String lon = lonInput.getText().toString().trim();
        if (!lat.equals(latPrefill) || !lon.equals(lonPrefill)) {
            double la = parseNumber(lat), lo = parseNumber(lon);
            if (Double.isNaN(la) || la < -90 || la > 90) err = ctx.getString(R.string.pt_lat);
            else if (Double.isNaN(lo) || lo < -180 || lo > 180) err = ctx.getString(R.string.pt_lon);
            else {
                item.position = new GeoPos(la, lo, item.position.altHae);
                latPrefill = lat;
                lonPrefill = lon;
            }
        }
        double agl = aglField.read();
        if (Double.isNaN(agl) || agl < 0) err = err == null ? ctx.getString(R.string.pt_agl_fmt,
                settings.getUnitSystem().abbreviation) : err;
        else item.aglMeters = agl;
        double eng = engageField.read();
        if (Double.isNaN(eng) || eng <= 0) err = err == null ? ctx.getString(R.string.pt_max_engage_fmt,
                settings.getUnitSystem().abbreviation) : err;
        else item.maxEngagementDistMeters = eng;
        String specific = readSpecificFields(item);
        return err != null ? err : specific;
    }

    private T save() {
        T item = editing;
        if (item == null) return null;
        String err = readForm(item);
        if (err != null || !item.isValid()) {
            toast(ctx.getString(R.string.pt_invalid_fmt, err == null ? item.label : err));
            return null;
        }
        item.touch();
        editing = null;
        repository.upsert(item);
        showList();
        return item;
    }

    private void confirmDelete(final String uid, final String label, final boolean fromEditor) {
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(ctx.getString(R.string.pt_delete_title))
                .setMessage(ctx.getString(R.string.pt_delete_msg_fmt, label))
                .setPositiveButton(ctx.getString(R.string.pt_delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (fromEditor) editing = null;
                        repository.remove(uid);
                        toast(ctx.getString(R.string.pt_deleted_fmt, label));
                        if (fromEditor) showList();
                    }
                })
                .setNegativeButton(ctx.getString(R.string.pt_cancel), null)
                .show();
    }

    // ------------------------------------------------------------- form helpers

    /** Unit-aware distance input that keeps the exact stored value when untouched. */
    protected final class DistanceField {
        final EditText input;
        private String prefill;
        private double original;

        DistanceField(EditText input, double meters) {
            this.input = input;
            this.original = meters;
            this.prefill = settings.formatNumber(meters);
            input.setText(prefill);
        }

        /** Meters, or NaN when unparsable. */
        double read() {
            String t = input.getText().toString().trim();
            if (t.equals(prefill)) return original;
            double m = settings.parseDistance(t);
            if (Double.isNaN(m) || Double.isInfinite(m)) return Double.NaN;
            original = m;
            prefill = t;
            return m;
        }
    }

    protected DistanceField distanceField(LinearLayout form, String label, double meters) {
        EditText e = field(form, label, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, null);
        return new DistanceField(e, meters);
    }

    protected EditText field(LinearLayout form, String label, int inputType, String value) {
        TextView l = text(label, 0xFFB0BEC5);
        l.setPadding(0, dp(6), 0, 0);
        form.addView(l, matchWrap());
        EditText e = new EditText(ctx);
        e.setInputType(inputType);
        e.setTextColor(0xFFFFFFFF);
        if (value != null) e.setText(value);
        form.addView(e, matchWrap());
        return e;
    }

    protected EditText numberField(LinearLayout form, String label, double value, boolean signed) {
        int type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0);
        return field(form, label, type, String.format(Locale.US, "%.1f", value));
    }

    static double parseNumber(String s) {
        if (s == null) return Double.NaN;
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isInfinite(v) ? Double.NaN : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Swatch row; {@code holder[0]} receives the chosen color. */
    protected void swatchRow(final LinearLayout container, final int[] holder, final Runnable onChange) {
        container.removeAllViews();
        for (final int c : ExclusionZonePanel.PRESET_COLORS) {
            View sw = new View(ctx);
            sw.setBackground(swatchDrawable(c, (holder[0] | 0xFF000000) == c));
            sw.setContentDescription(String.format("#%06X", c & 0xFFFFFF));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    holder[0] = c;
                    swatchRow(container, holder, onChange);
                    if (onChange != null) onChange.run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
            lp.rightMargin = dp(4);
            container.addView(sw, lp);
        }
    }

    /** Labelled swatch row bound to {@link #selectedColor}. */
    protected void colorPicker(LinearLayout form, String label, int initial) {
        selectedColor = initial | 0xFF000000;
        form.addView(text(label, 0xFFB0BEC5), matchWrap());
        final int[] holder = {selectedColor};
        LinearLayout row = horizontal();
        swatchRow(row, holder, new Runnable() {
            @Override
            public void run() {
                selectedColor = holder[0];
            }
        });
        form.addView(row, matchWrap());
    }

    /** Labelled 0..100 seek bar; label text from {@code fmtRes} with the percentage. */
    protected SeekBar percentSeek(LinearLayout form, final int fmtRes, int percent) {
        final TextView l = text(ctx.getString(fmtRes, percent), 0xFFB0BEC5);
        l.setPadding(0, dp(6), 0, 0);
        form.addView(l, matchWrap());
        SeekBar s = new SeekBar(ctx);
        s.setMax(100);
        s.setProgress(percent);
        s.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                l.setText(ctx.getString(fmtRes, p));
            }
        });
        form.addView(s, matchWrap());
        return s;
    }

    protected GradientDrawable swatchDrawable(int fill, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(4));
        d.setStroke(selected ? dp(3) : dp(1), selected ? 0xFFFFC107 : 0xFF000000);
        return d;
    }

    protected LinearLayout vertical() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(8), dp(4), dp(8), dp(4));
        return l;
    }

    protected LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    protected TextView text(String s, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        return t;
    }

    protected TextView heading(String s) {
        TextView t = text(s, 0xFF4FC3F7);
        t.setTextSize(17f);
        t.setPadding(0, dp(8), 0, dp(2));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    protected Button button(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private ScrollView scroll(View content) {
        ScrollView sv = new ScrollView(ctx);
        sv.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    protected static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void setRoot(View v) {
        if (root == null) return;
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    protected int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    protected void toast(String msg) {
        Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
