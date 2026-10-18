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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.analysis.AuthorityEvaluator;
import com.skiarkt.shooter.analysis.PolygonBuffer;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.AuthorityType;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.DefendedAsset;
import com.skiarkt.shooter.model.EngagementAuthorityZone;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.model.PolygonItem;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.tools.ExclusionZoneDrawTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Engagement Authority side-panel section (ROADMAP R6): list / create / edit buffer zones
 * offset from an exclusion zone, defended asset or drawn boundary, plus the effector
 * authority check. Built programmatically (no layout XML). UI thread only.
 */
public class AuthorityPanel {

    static final int[] PRESET_COLORS = {
            AuthorityType.NON_KINETIC.defaultColor,
            AuthorityType.KINETIC.defaultColor,
            AuthorityType.NON_HOSTILE.defaultColor,
            0xFFFF1744, 0xFFFFEB3B, 0xFF2979FF, 0xFFD500F9, 0xFFFFFFFF, 0xFF616161
    };

    private final Context pluginContext;
    private final MapView mapView;
    private final SettingsManager settings;
    private final PointDefenseRepository repository;
    private final OverlayManager overlayManager;
    private final Runnable onBack;

    private FrameLayout root;
    private ExclusionZoneDrawTool drawTool;

    // list state
    private LinearLayout listContainer;
    private LinearLayout zoneList;
    private TextView checkText;
    private LinearLayout drawBar;
    private TextView drawCount;
    private Button closeButton;

    // edit state
    private EngagementAuthorityZone editing;
    private boolean editingIsNew;
    private EditText labelInput;
    private EditText bufferInput;
    private EditText remarksInput;
    private String bufferPrefill;
    private double bufferOriginal;
    private Button typeButton;
    private AuthorityType selectedType;
    private int selectedColor;
    private LinearLayout swatches;
    private SeekBar opacitySeek;
    private CheckBox dashedCheck;

    public AuthorityPanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, OverlayManager overlayManager, Runnable onBack) {
        this.pluginContext = pluginContext;
        this.mapView = mapView;
        this.settings = settings;
        this.repository = repository;
        this.overlayManager = overlayManager;
        this.onBack = onBack;
    }

    public View createView() {
        cancelDrawing();
        editing = null;
        root = new FrameLayout(pluginContext);
        showList();
        return root;
    }

    public void refresh() {
        if (isBusy() || zoneList == null) return;
        populateList();
    }

    public boolean isBusy() {
        return editing != null || (drawTool != null && drawTool.isActive());
    }

    public void onHidden() {
        cancelDrawing();
    }

    public void dispose() {
        cancelDrawing();
    }

    // =================================================================== LIST

    private void showList() {
        editing = null;
        LinearLayout v = column();
        Button back = button("\u2190 Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelDrawing();
                onBack.run();
            }
        });
        v.addView(back, wrap());
        v.addView(title("Engagement Authority"));
        v.addView(hint("Rule: an effector class is authorized against a threat point only when the point "
                + "is inside that class's authority zone AND outside every non-hostile (weapons hold) zone. "
                + "Buffer: positive = outward from the base perimeter, negative = inward."));

        // draw bar (hidden until drawing)
        drawBar = column();
        drawBar.setVisibility(View.GONE);
        drawBar.addView(hint("Tap the map to add boundary vertices, then Close."));
        drawCount = label("0 vertices");
        drawBar.addView(drawCount);
        LinearLayout bar = row();
        Button undo = button("Undo");
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawTool != null) drawTool.undoLastVertex();
            }
        });
        closeButton = button("Close");
        closeButton.setEnabled(false);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawTool != null) drawTool.closePolygon();
            }
        });
        Button cancel = button("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelDrawing();
            }
        });
        bar.addView(undo, weight());
        bar.addView(closeButton, weight());
        bar.addView(cancel, weight());
        drawBar.addView(bar);
        v.addView(drawBar);

        listContainer = column();
        listContainer.addView(header("Zones"));
        zoneList = column();
        listContainer.addView(zoneList);
        LinearLayout actions = row();
        Button fromBase = button("New from zone / asset");
        fromBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickBase();
            }
        });
        Button draw = button("Draw boundary");
        draw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDraw();
            }
        });
        actions.addView(fromBase, weight());
        actions.addView(draw, weight());
        listContainer.addView(actions);
        listContainer.addView(header("Authority check"));
        checkText = label("");
        listContainer.addView(checkText);
        v.addView(listContainer);

        populateList();
        setRoot(scroll(v));
    }

    private void populateList() {
        zoneList.removeAllViews();
        PointDefensePlan plan = repository.getPlan();
        List<EngagementAuthorityZone> zones = repository.getItems(ItemKind.AUTHORITY_ZONE);
        if (zones.isEmpty()) zoneList.addView(hint("No authority zones yet."));
        for (final EngagementAuthorityZone z : zones) {
            LinearLayout r = row();
            r.setAlpha(VisibilityUi.alpha(repository, z.uid));
            View sw = new View(pluginContext);
            sw.setBackground(swatchDrawable(z.fillColor | 0xFF000000, z.color | 0xFF000000, false));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(20), dp(20));
            slp.rightMargin = dp(6);
            r.addView(sw, slp);
            TextView t = label(VisibilityUi.label(pluginContext, repository, z.uid,
                    z.label + " \u2014 " + z.authorityType.shortTag + " " + signedDistance(z.bufferMeters)
                    + (z.isBaseMissing(plan) ? "  \u26A0 base missing (snapshot)" : "")));
            t.setPadding(0, dp(8), 0, dp(8));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEdit(z.copy(), false);
                }
            });
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(VisibilityUi.eye(pluginContext, mapView.getContext(), repository, z.uid, z.label), wrap());
            Button del = button("\u2715");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete(z.uid, z.label, false);
                }
            });
            r.addView(del, wrap());
            zoneList.addView(r);
        }
        checkText.setText(checkSummary(plan));
    }

    /** Human-readable authority check of every kinetic / NK effector. */
    static String checkSummary(PointDefensePlan plan) {
        List<AuthorityEvaluator.EffectorCheck> checks = AuthorityEvaluator.checkAll(plan);
        if (checks.isEmpty()) return "No placed kinetic or non-kinetic effectors to check.";
        StringBuilder sb = new StringBuilder();
        int violations = 0;
        for (AuthorityEvaluator.EffectorCheck c : checks) {
            if (c.hasViolation()) violations++;
            sb.append(c.summary()).append('\n');
        }
        sb.insert(0, violations == 0 ? "All effectors within authority.\n\n"
                : violations + " effector(s) flagged.\n\n");
        return sb.toString().trim();
    }

    private void pickBase() {
        final List<PolygonItem> bases = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Object o : repository.getItems(ItemKind.EXCLUSION_ZONE)) {
            ExclusionZone z = (ExclusionZone) o;
            if (z.isValid()) {
                bases.add(z);
                names.add("Exclusion zone: " + z.label);
            }
        }
        for (Object o : repository.getItems(ItemKind.DEFENDED_ASSET)) {
            DefendedAsset a = (DefendedAsset) o;
            if (a.isValid()) {
                bases.add(a);
                names.add("Defended asset: " + a.label);
            }
        }
        if (bases.isEmpty()) {
            toast("No exclusion zones or defended assets yet \u2014 use Draw boundary.");
            return;
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Base perimeter")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        PolygonItem b = bases.get(which);
                        EngagementAuthorityZone z = newZone();
                        z.baseUid = b.uid;
                        z.label = b.label + " " + z.authorityType.shortTag;
                        z.syncSnapshot(repository.getPlan());
                        showEdit(z, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EngagementAuthorityZone newZone() {
        EngagementAuthorityZone z = new EngagementAuthorityZone();
        z.authorityType = AuthorityType.KINETIC;
        z.applyTypeColors();
        z.bufferMeters = 0;
        return z;
    }

    // =============================================================== DRAWING

    private void startDraw() {
        if (drawTool == null) {
            drawTool = new ExclusionZoneDrawTool(mapView,
                    overlayManager == null ? null : overlayManager.getDrawingGroup());
        }
        boolean ok = drawTool.begin(ExclusionZoneDrawTool.Mode.POLYGON,
                AuthorityType.KINETIC.defaultColor, drawListener);
        if (!ok) {
            toast("Drawing is unavailable right now.");
            return;
        }
        drawCount.setText("0 vertices");
        closeButton.setEnabled(false);
        drawBar.setVisibility(View.VISIBLE);
        listContainer.setVisibility(View.GONE);
    }

    private final ExclusionZoneDrawTool.Listener drawListener = new ExclusionZoneDrawTool.Listener() {
        @Override
        public void onVerticesChanged(int count, boolean canFinish) {
            if (drawCount != null) drawCount.setText(count + " vertices");
            if (closeButton != null) closeButton.setEnabled(canFinish);
        }

        @Override
        public void onPolygonClosed(List<GeoPos> vertices) {
            EngagementAuthorityZone z = newZone();
            z.points = new ArrayList<>(vertices);
            z.label = "Authority " + (repository.getPlan().count(ItemKind.AUTHORITY_ZONE) + 1);
            showEdit(z, true);
        }

        @Override
        public void onLineComplete(GeoPos start, GeoPos end) {
            exitDrawBar();
        }

        @Override
        public void onDrawCancelled() {
            exitDrawBar();
        }
    };

    private void exitDrawBar() {
        if (drawBar != null) drawBar.setVisibility(View.GONE);
        if (listContainer != null) listContainer.setVisibility(View.VISIBLE);
    }

    private void cancelDrawing() {
        if (drawTool != null && drawTool.isActive()) drawTool.cancel();
    }

    // =============================================================== EDITING

    private void showEdit(final EngagementAuthorityZone zone, boolean isNew) {
        editing = zone;
        editingIsNew = isNew;
        zoneList = null;
        PointDefensePlan plan = repository.getPlan();
        LinearLayout v = column();
        v.addView(title(isNew ? "New authority zone" : "Edit authority zone"));

        BasePlanItem base = zone.baseUid == null ? null : plan.findByUid(zone.baseUid);
        String baseText = zone.baseUid == null
                ? "Base: drawn boundary (" + zone.points.size() + " vertices)"
                : base != null ? "Base: " + base.getKind().displayName + " \u201C" + base.label + "\u201D"
                        + " (follows edits to the base)"
                : "\u26A0 Base item missing \u2014 using the stored snapshot (" + zone.points.size() + " vertices)";
        v.addView(hint(baseText));
        if (!isNew) v.addView(hint("UID (link from a kinetic effector): " + zone.uid));

        v.addView(label("Label"));
        labelInput = new EditText(pluginContext);
        labelInput.setSingleLine(true);
        labelInput.setText(zone.label);
        v.addView(labelInput);

        v.addView(label("Authority type"));
        selectedType = zone.authorityType;
        selectedColor = zone.fillColor | 0xFF000000;
        typeButton = button(selectedType.displayName);
        typeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean defaultColor = selectedColor == (selectedType.defaultColor | 0xFF000000);
                selectedType = selectedType.next();
                if (defaultColor) {
                    selectedColor = selectedType.defaultColor | 0xFF000000;
                    buildSwatches();
                }
                typeButton.setText(selectedType.displayName);
            }
        });
        v.addView(typeButton);

        v.addView(label("Buffer (" + settings.getUnitSystem().abbreviation
                + "; positive = outward, negative = inward)"));
        bufferInput = new EditText(pluginContext);
        bufferInput.setSingleLine(true);
        bufferInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        bufferOriginal = zone.bufferMeters;
        bufferPrefill = settings.formatNumber(zone.bufferMeters);
        bufferInput.setText(bufferPrefill);
        v.addView(bufferInput);

        v.addView(label("Color"));
        swatches = row();
        v.addView(swatches);
        buildSwatches();

        final TextView opacityLabel = label("");
        v.addView(opacityLabel);
        opacitySeek = new SeekBar(pluginContext);
        opacitySeek.setMax(100);
        opacitySeek.setProgress(Math.round(zone.fillOpacity * 100));
        opacityLabel.setText("Fill opacity: " + opacitySeek.getProgress() + "%");
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                opacityLabel.setText("Fill opacity: " + p + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        v.addView(opacitySeek);

        dashedCheck = new CheckBox(pluginContext);
        dashedCheck.setText("Dashed outline");
        dashedCheck.setTextColor(0xFFFFFFFF);
        dashedCheck.setChecked(zone.strokeDashed);
        v.addView(dashedCheck);

        v.addView(label("Remarks"));
        remarksInput = new EditText(pluginContext);
        remarksInput.setText(zone.remarks);
        v.addView(remarksInput);

        LinearLayout b1 = row();
        Button save = button("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EngagementAuthorityZone s = save();
                if (s != null) toast("Saved " + s.label);
            }
        });
        Button share = button("Share");
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EngagementAuthorityZone s = save();
                if (s == null) return;
                BasePlanItem stored = repository.find(s.uid);
                boolean ok = stored != null && repository.share(stored);
                toast(ok ? "Shared " + s.label : "Share failed");
            }
        });
        b1.addView(save, weight());
        b1.addView(share, weight());
        v.addView(b1);
        LinearLayout b2 = row();
        Button delete = button("Delete");
        delete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete(zone.uid, labelInput.getText().toString().trim(), true);
            }
        });
        Button cancel = button("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showList();
            }
        });
        b2.addView(delete, weight());
        b2.addView(cancel, weight());
        v.addView(b2);
        setRoot(scroll(v));
    }

    private EngagementAuthorityZone save() {
        EngagementAuthorityZone z = editing;
        if (z == null) return null;
        String b = bufferInput.getText().toString().trim();
        double buffer = bufferOriginal;
        if (!b.equals(bufferPrefill)) {
            buffer = settings.parseDistance(b);
            if (Double.isNaN(buffer) || Double.isInfinite(buffer)
                    || Math.abs(buffer) > EngagementAuthorityZone.MAX_ABS_BUFFER_M) {
                toast("Invalid buffer distance");
                return null;
            }
        }
        String label = labelInput.getText().toString().trim();
        if (!label.isEmpty()) z.label = label;
        z.remarks = remarksInput.getText().toString();
        z.authorityType = selectedType;
        z.bufferMeters = buffer;
        z.fillColor = selectedColor;
        z.color = selectedColor;
        z.fillOpacity = opacitySeek.getProgress() / 100f;
        z.strokeDashed = dashedCheck.isChecked();
        z.normalize();
        z.syncSnapshot(repository.getPlan());
        if (!z.isValid()) {
            toast("Zone needs a base perimeter with at least 3 vertices");
            return null;
        }
        if (PolygonBuffer.offsetRing(z.points, z.bufferMeters).isEmpty()) {
            toast("Warning: the inward buffer collapses the base \u2014 the zone is empty");
        }
        z.touch();
        editing = null;
        repository.upsert(z);
        showList();
        return z;
    }

    private void confirmDelete(final String uid, final String label, final boolean fromEditor) {
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Delete authority zone")
                .setMessage("Delete \u201C" + label + "\u201D?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (fromEditor) editing = null;
                        repository.remove(uid);
                        toast("Deleted " + label);
                        if (fromEditor) showList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildSwatches() {
        swatches.removeAllViews();
        int size = dp(30);
        for (final int c : PRESET_COLORS) {
            View sw = new View(pluginContext);
            sw.setBackground(swatchDrawable(c, 0xFF000000, selectedColor == c));
            sw.setContentDescription(String.format("#%06X", c & 0xFFFFFF));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedColor = c;
                    buildSwatches();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = dp(4);
            swatches.addView(sw, lp);
        }
    }

    // =============================================================== helpers

    private String signedDistance(double m) {
        return (m > 0 ? "+" : m < 0 ? "\u2212" : "\u00B1") + settings.formatDistance(Math.abs(m));
    }

    private GradientDrawable swatchDrawable(int fill, int border, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(4));
        d.setStroke(selected ? dp(3) : dp(1), selected ? 0xFFFFC107 : border);
        return d;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(pluginContext);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(8), dp(4), dp(8), dp(4));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(pluginContext);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(pluginContext);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView title(String s) {
        TextView t = text(s, 18, 0xFFFFFFFF);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 15, 0xFFFFC107);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView label(String s) {
        return text(s, 13, 0xFFFFFFFF);
    }

    private TextView hint(String s) {
        TextView t = text(s, 12, 0xFFB0BEC5);
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(pluginContext);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View scroll(View content) {
        ScrollView s = new ScrollView(pluginContext);
        s.addView(content);
        return s;
    }

    private void setRoot(View v) {
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private int dp(int v) {
        return Math.round(v * pluginContext.getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
