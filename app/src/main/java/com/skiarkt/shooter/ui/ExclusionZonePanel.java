package com.skiarkt.shooter.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.analysis.InterceptAltitudeCalculator;
import com.skiarkt.shooter.analysis.InterceptAltitudeResult;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.ExclusionZoneShape;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.PlanColors;
import com.skiarkt.shooter.model.ThreatCorridor;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.tools.ExclusionZoneDrawTool;
import com.skiarkt.shooter.tools.PolygonDrawState;
import com.skiarkt.shooter.util.DtedHelper;
import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Exclusion Zones side-panel section: CRUD list, modal polygon draw tool, properties
 * editor and the minimum intercept altitude calculator.
 *
 * <p>Three states share one container: LIST (zone list + calculator), DRAWING (draw bar
 * only, list hidden) and EDITING (properties form). Plan-change refreshes only touch the
 * list state, so an open form or draw session is never clobbered. UI thread only.
 */
public class ExclusionZonePanel {

    /** Preset swatches offered for fill and stroke. */
    static final int[] PRESET_COLORS = {
            PlanColors.EXCLUSION_ZONE, // orange
            0xFFFF1744, // red
            0xFFFFEB3B, // yellow
            0xFF00E676, // green
            0xFF00E5FF, // cyan
            0xFF2979FF, // blue
            0xFFD500F9, // purple
            0xFFFFFFFF, // white
            0xFF616161  // grey
    };

    private enum DrawPurpose { NEW_ZONE, NEW_CIRCLE, NEW_RECT, REDRAW, INGRESS_LINE }

    private final Context pluginContext;
    private final MapView mapView;
    private final SettingsManager settings;
    private final PointDefenseRepository repository;
    private final DtedHelper dtedHelper;
    private final OverlayManager overlayManager;
    private final Runnable onBack;

    private FrameLayout root;
    private ExclusionZoneDrawTool drawTool;
    private DrawPurpose drawPurpose;

    // list state views (null when another state is showing)
    private View listView;
    private LinearLayout zoneList;
    private TextView listEmpty;
    private Spinner sourceSpinner;
    private EditText spacingInput;
    private EditText bufferInput;
    private String spacingPrefill;
    private String bufferPrefill;
    private CheckBox showOnMap;
    private TextView resultText;
    private TextView profileText;
    private View drawBar;
    private View listSection;
    private TextView drawCount;
    private Button closeButton;

    // intercept state (survives view rebuilds)
    private final List<Object> sourceItems = new ArrayList<>();
    private List<GeoPos> drawnLine;
    private Object selectedSource;
    private InterceptAltitudeResult lastResult;

    // edit state
    private ExclusionZone editing;
    private boolean editingIsNew;
    private String heightPrefill;
    private double heightOriginalMeters;
    private int selectedFill;
    private int selectedStroke;
    private EditText labelInput;
    private EditText heightInput;
    private EditText remarksInput;
    private SeekBar opacitySeek;
    private SeekBar strokeWidthSeek;
    private CheckBox dashedCheck;
    private LinearLayout fillSwatches;
    private LinearLayout strokeSwatches;
    // R3 shape parameter inputs (null for polygons)
    private EditText centerInput;
    private EditText radiusInput;
    private EditText rectWidthInput;
    private EditText rectLengthInput;
    private EditText rotationInput;

    public ExclusionZonePanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, DtedHelper dtedHelper, OverlayManager overlayManager,
            Runnable onBack) {
        this.pluginContext = pluginContext;
        this.mapView = mapView;
        this.settings = settings;
        this.repository = repository;
        this.dtedHelper = dtedHelper;
        this.overlayManager = overlayManager;
        this.onBack = onBack;
    }

    /** (Re)creates the section view in LIST state, cancelling any draw / edit in progress. */
    public View createView() {
        cancelDrawing();
        editing = null;
        root = new FrameLayout(pluginContext);
        showList();
        return root;
    }

    /** Plan changed: refresh the list unless the user is editing or drawing. */
    public void refresh() {
        if (isBusy() || listView == null) return;
        populateList();
        populateSources();
    }

    public boolean isBusy() {
        return editing != null || (drawTool != null && drawTool.isActive());
    }

    /** Section hidden or drop-down closed: leave draw mode so map taps behave normally. */
    public void onHidden() {
        cancelDrawing();
    }

    public void dispose() {
        cancelDrawing();
        if (overlayManager != null) overlayManager.clearInterceptResult();
    }

    // =================================================================== LIST

    private void showList() {
        editing = null;
        View v = PluginLayoutInflater.inflate(pluginContext, R.layout.exclusion_zone_layout, null);
        listView = v;
        zoneList = v.findViewById(R.id.ez_list);
        listEmpty = v.findViewById(R.id.ez_list_empty);
        sourceSpinner = v.findViewById(R.id.ez_intercept_source);
        spacingInput = v.findViewById(R.id.ez_spacing);
        bufferInput = v.findViewById(R.id.ez_buffer);
        showOnMap = v.findViewById(R.id.ez_show_on_map);
        resultText = v.findViewById(R.id.ez_intercept_result);
        profileText = v.findViewById(R.id.ez_intercept_profile);
        drawBar = v.findViewById(R.id.ez_draw_bar);
        listSection = v.findViewById(R.id.ez_list_section);
        drawCount = v.findViewById(R.id.ez_draw_count);
        closeButton = v.findViewById(R.id.ez_btn_close);

        String unit = settings.getUnitSystem().abbreviation;
        ((TextView) v.findViewById(R.id.ez_spacing_label))
                .setText(pluginContext.getString(R.string.ez_intercept_spacing_fmt, unit));
        ((TextView) v.findViewById(R.id.ez_buffer_label))
                .setText(pluginContext.getString(R.string.ez_intercept_buffer_fmt, unit));
        spacingPrefill = settings.formatNumber(settings.getInterceptSampleSpacingMeters());
        bufferPrefill = settings.formatNumber(settings.getInterceptSafetyBufferMeters());
        spacingInput.setText(spacingPrefill);
        bufferInput.setText(bufferPrefill);
        showOnMap.setChecked(settings.isInterceptShownOnMap());
        showOnMap.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                settings.setInterceptShownOnMap(checked);
                if (overlayManager == null) return;
                if (checked && lastResult != null) overlayManager.showInterceptResult(lastResult);
                else overlayManager.clearInterceptResult();
            }
        });

        v.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelDrawing();
                onBack.run();
            }
        });
        v.findViewById(R.id.ez_btn_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDraw(DrawPurpose.NEW_ZONE);
            }
        });
        v.findViewById(R.id.ez_btn_new_circle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDraw(DrawPurpose.NEW_CIRCLE);
            }
        });
        v.findViewById(R.id.ez_btn_new_rect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDraw(DrawPurpose.NEW_RECT);
            }
        });
        v.findViewById(R.id.ez_btn_draw_line).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDraw(DrawPurpose.INGRESS_LINE);
            }
        });
        v.findViewById(R.id.ez_btn_compute).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                computeIntercept();
            }
        });
        v.findViewById(R.id.ez_btn_undo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawTool != null) drawTool.undoLastVertex();
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (drawTool != null) drawTool.closePolygon();
            }
        });
        v.findViewById(R.id.ez_btn_cancel_draw).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelDrawing();
            }
        });

        populateList();
        populateSources();
        showResult();
        setRoot(v);
    }

    private void populateList() {
        zoneList.removeAllViews();
        List<ExclusionZone> zones = repository.getItems(ItemKind.EXCLUSION_ZONE);
        listEmpty.setVisibility(zones.isEmpty() ? View.VISIBLE : View.GONE);
        for (final ExclusionZone z : zones) {
            LinearLayout row = new LinearLayout(pluginContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setAlpha(VisibilityUi.alpha(repository, z.uid));

            View swatch = new View(pluginContext);
            swatch.setBackground(swatchDrawable(z.getFillArgb() | 0xFF000000, z.getStrokeColor(), false));
            int s = dp(20);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(s, s);
            slp.rightMargin = dp(6);
            row.addView(swatch, slp);

            TextView text = new TextView(pluginContext);
            text.setText(VisibilityUi.label(pluginContext, repository, z.uid,
                    pluginContext.getString(R.string.ez_list_item_fmt, z.label,
                    settings.formatDistance(z.heightAglMeters), z.points.size())));
            text.setTextColor(0xFFFFFFFF);
            text.setPadding(0, dp(8), 0, dp(8));
            text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEdit(z.copy(), false);
                }
            });
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(VisibilityUi.eye(pluginContext, mapView.getContext(), repository, z.uid, z.label),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));

            Button del = new Button(pluginContext);
            del.setText("\u2715");
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete(z.uid, z.label, false);
                }
            });
            row.addView(del, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            zoneList.addView(row);
        }
    }

    private void populateSources() {
        sourceItems.clear();
        List<String> labels = new ArrayList<>();
        if (drawnLine != null) {
            sourceItems.add(drawnLine);
            labels.add(pluginContext.getString(R.string.ez_intercept_drawn_line) + " ("
                    + settings.formatDistance(GeoUtils.distanceMeters(drawnLine.get(0), drawnLine.get(1))) + ")");
        }
        List<ThreatCorridor> corridors = repository.getItems(ItemKind.THREAT_CORRIDOR);
        for (ThreatCorridor c : corridors) {
            if (!c.isValid()) continue;
            sourceItems.add(c);
            labels.add(c.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(pluginContext,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(adapter);
        int idx = sourceItems.indexOf(selectedSource);
        if (selectedSource instanceof ThreatCorridor) {
            for (int i = 0; i < sourceItems.size(); i++) {
                Object o = sourceItems.get(i);
                if (o instanceof ThreatCorridor && ((ThreatCorridor) o).uid.equals(((ThreatCorridor) selectedSource).uid)) {
                    idx = i;
                }
            }
        }
        if (idx >= 0) sourceSpinner.setSelection(idx);
    }

    // ============================================================ INTERCEPT

    private void computeIntercept() {
        applyInterceptInputs();
        int pos = sourceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= sourceItems.size()) {
            toast(pluginContext.getString(R.string.ez_intercept_need_path));
            return;
        }
        selectedSource = sourceItems.get(pos);
        List<ExclusionZone> zones = new ArrayList<>();
        for (Object o : repository.getItems(ItemKind.EXCLUSION_ZONE)) {
            ExclusionZone z = (ExclusionZone) o;
            if (z.isValid()) zones.add(z);
        }
        if (zones.isEmpty()) toast(pluginContext.getString(R.string.ez_intercept_need_zones));

        InterceptAltitudeCalculator calc;
        try {
            calc = new InterceptAltitudeCalculator(dtedHelper, settings.getInterceptSampleSpacingMeters(),
                    settings.getInterceptSafetyBufferMeters());
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
            return;
        }
        InterceptAltitudeResult r;
        if (selectedSource instanceof ThreatCorridor) {
            r = calc.computeForCorridor(zones, (ThreatCorridor) selectedSource);
        } else {
            @SuppressWarnings("unchecked")
            List<GeoPos> line = (List<GeoPos>) selectedSource;
            r = calc.computeForLine(zones, line.get(0), line.get(1));
        }
        lastResult = r;
        showResult();
        if (overlayManager != null) {
            if (settings.isInterceptShownOnMap() && r != null) overlayManager.showInterceptResult(r);
            else overlayManager.clearInterceptResult();
        }
    }

    /** Persists spacing / buffer inputs; untouched fields keep the exact stored value. */
    private void applyInterceptInputs() {
        String sp = spacingInput.getText().toString().trim();
        if (!sp.equals(spacingPrefill)) {
            double m = settings.parseDistance(sp);
            if (!Double.isNaN(m) && m > 0) settings.setInterceptSampleSpacingMeters(m);
        }
        String bf = bufferInput.getText().toString().trim();
        if (!bf.equals(bufferPrefill)) {
            double m = settings.parseDistance(bf);
            if (!Double.isNaN(m) && m >= 0) settings.setInterceptSafetyBufferMeters(m);
        }
        spacingPrefill = settings.formatNumber(settings.getInterceptSampleSpacingMeters());
        bufferPrefill = settings.formatNumber(settings.getInterceptSafetyBufferMeters());
        spacingInput.setText(spacingPrefill);
        bufferInput.setText(bufferPrefill);
    }

    private void showResult() {
        if (resultText == null) return;
        if (lastResult == null) {
            resultText.setText(R.string.ez_intercept_none);
            profileText.setText("");
            return;
        }
        resultText.setText(lastResult.format(settings.getUnitSystem(), settings.getPrecision()));
        StringBuilder sb = new StringBuilder();
        for (InterceptAltitudeResult.ConstraintSegment s : lastResult.getSegments()) {
            sb.append(InterceptAltitudeResult.formatSegment(s, settings.getUnitSystem(), settings.getPrecision()))
                    .append('\n');
        }
        profileText.setText(sb.toString().trim());
    }

    // =============================================================== DRAWING

    private void startDraw(DrawPurpose purpose) {
        if (drawTool == null) {
            drawTool = new ExclusionZoneDrawTool(mapView,
                    overlayManager == null ? null : overlayManager.getDrawingGroup());
        }
        int color = purpose == DrawPurpose.INGRESS_LINE ? 0xFFFFFFFF
                : (editing != null ? editing.getStrokeColor()
                        : settings.getColor(SettingsManager.ColorKey.EXCLUSION_ZONE));
        if (purpose == DrawPurpose.REDRAW) showList(); // need the draw bar
        drawPurpose = purpose;
        boolean twoTap = purpose == DrawPurpose.INGRESS_LINE || purpose == DrawPurpose.NEW_CIRCLE
                || purpose == DrawPurpose.NEW_RECT;
        boolean ok = drawTool.begin(twoTap
                ? ExclusionZoneDrawTool.Mode.LINE : ExclusionZoneDrawTool.Mode.POLYGON, color, drawListener);
        if (!ok) {
            toast(pluginContext.getString(R.string.ez_draw_unavailable));
            if (purpose == DrawPurpose.REDRAW && pendingRedraw != null) showEdit(pendingRedraw, editingIsNew);
            return;
        }
        int hint = purpose == DrawPurpose.INGRESS_LINE ? R.string.ez_draw_hint_line
                : purpose == DrawPurpose.NEW_CIRCLE ? R.string.ez_draw_hint_circle
                : purpose == DrawPurpose.NEW_RECT ? R.string.ez_draw_hint_rect
                : R.string.ez_draw_hint_polygon;
        ((TextView) listView.findViewById(R.id.ez_draw_hint)).setText(hint);
        closeButton.setVisibility(twoTap ? View.GONE : View.VISIBLE);
        drawBar.setVisibility(View.VISIBLE);
        listSection.setVisibility(View.GONE);
    }

    /** Zone being redrawn (form values captured before switching to draw mode). */
    private ExclusionZone pendingRedraw;

    private final ExclusionZoneDrawTool.Listener drawListener = new ExclusionZoneDrawTool.Listener() {
        @Override
        public void onVerticesChanged(int count, boolean canFinish) {
            if (drawCount != null) drawCount.setText(pluginContext.getString(R.string.ez_draw_count_fmt, count));
            if (closeButton != null) closeButton.setEnabled(canFinish);
        }

        @Override
        public void onPolygonClosed(List<GeoPos> vertices) {
            if (drawPurpose == DrawPurpose.REDRAW && pendingRedraw != null) {
                ExclusionZone z = pendingRedraw;
                pendingRedraw = null;
                z.points = new ArrayList<>(vertices);
                z.convertToPolygon(); // a hand-drawn footprint replaces circle / rectangle params
                showEdit(z, editingIsNew);
                return;
            }
            ExclusionZone z = newStyledZone();
            z.points = new ArrayList<>(vertices);
            showEdit(z, true);
        }

        @Override
        public void onLineComplete(GeoPos start, GeoPos end) {
            if (drawPurpose == DrawPurpose.NEW_CIRCLE || drawPurpose == DrawPurpose.NEW_RECT) {
                ExclusionZone z = newStyledZone();
                if (drawPurpose == DrawPurpose.NEW_CIRCLE) {
                    z.setCircle(start, Math.max(1.0, GeoUtils.distanceMeters(start, end)));
                } else {
                    z.setRectangleFromCorners(start, end);
                }
                exitDrawBar();
                showEdit(z, true);
                return;
            }
            onIngressLine(start, end);
        }

        @Override
        public void onDrawCancelled() {
            if (drawPurpose == DrawPurpose.REDRAW && pendingRedraw != null) {
                ExclusionZone z = pendingRedraw;
                pendingRedraw = null;
                showEdit(z, editingIsNew);
                return;
            }
            exitDrawBar();
        }
    };

    /** New zone pre-styled with the configured exclusion-zone colors. */
    private ExclusionZone newStyledZone() {
        ExclusionZone z = new ExclusionZone();
        z.label = "Zone " + (repository.getPlan().count(ItemKind.EXCLUSION_ZONE) + 1);
        int stroke = settings.getColor(SettingsManager.ColorKey.EXCLUSION_ZONE);
        z.setStrokeColor(stroke);
        z.fillColor = stroke;
        z.fillOpacity = 1f - settings.getZoneFillTransparency();
        z.normalizeStyle();
        return z;
    }

    private void onIngressLine(GeoPos start, GeoPos end) {
        List<GeoPos> line = new ArrayList<>();
        line.add(start);
        line.add(end);
        drawnLine = line;
        selectedSource = line;
        exitDrawBar();
        populateSources();
        computeIntercept();
    }

    private void exitDrawBar() {
        if (drawBar != null) drawBar.setVisibility(View.GONE);
        if (listSection != null) listSection.setVisibility(View.VISIBLE);
    }

    private void cancelDrawing() {
        if (drawTool != null && drawTool.isActive()) drawTool.cancel();
    }

    // =============================================================== EDITING

    private void showEdit(final ExclusionZone zone, boolean isNew) {
        editing = zone;
        editingIsNew = isNew;
        listView = null;
        View v = PluginLayoutInflater.inflate(pluginContext, R.layout.exclusion_zone_edit, null);
        ((TextView) v.findViewById(R.id.ez_edit_title))
                .setText(isNew ? R.string.ez_edit_title_new : R.string.ez_edit_title);
        ((TextView) v.findViewById(R.id.ez_geometry)).setText(pluginContext.getString(R.string.ez_geometry_fmt,
                zone.points.size(), settings.formatDistance(perimeter(zone.points))));
        v.findViewById(R.id.ez_warning).setVisibility(
                PolygonDrawState.isSelfIntersecting(zone.points) ? View.VISIBLE : View.GONE);

        buildShapeParams((LinearLayout) v.findViewById(R.id.ez_shape_params), zone);
        labelInput = v.findViewById(R.id.ez_label);
        heightInput = v.findViewById(R.id.ez_height);
        remarksInput = v.findViewById(R.id.ez_remarks);
        opacitySeek = v.findViewById(R.id.ez_fill_opacity);
        strokeWidthSeek = v.findViewById(R.id.ez_stroke_width);
        dashedCheck = v.findViewById(R.id.ez_stroke_dashed);
        fillSwatches = v.findViewById(R.id.ez_fill_swatches);
        strokeSwatches = v.findViewById(R.id.ez_stroke_swatches);
        final TextView opacityLabel = v.findViewById(R.id.ez_fill_opacity_label);
        final TextView widthLabel = v.findViewById(R.id.ez_stroke_width_label);

        ((TextView) v.findViewById(R.id.ez_height_label)).setText(
                pluginContext.getString(R.string.ez_height_fmt, settings.getUnitSystem().abbreviation));
        labelInput.setText(zone.label);
        heightOriginalMeters = zone.heightAglMeters;
        heightPrefill = settings.formatNumber(zone.heightAglMeters);
        heightInput.setText(heightPrefill);
        remarksInput.setText(zone.remarks);
        selectedFill = zone.fillColor | 0xFF000000;
        selectedStroke = zone.getStrokeColor() | 0xFF000000;
        opacitySeek.setProgress(Math.round(zone.fillOpacity * 100));
        strokeWidthSeek.setProgress((int) Math.round(zone.strokeWidth - ExclusionZone.MIN_STROKE_WIDTH));
        dashedCheck.setChecked(zone.strokeDashed);
        opacityLabel.setText(pluginContext.getString(R.string.ez_fill_opacity_fmt, opacitySeek.getProgress()));
        widthLabel.setText(pluginContext.getString(R.string.ez_stroke_width_fmt, strokeWidthPx()));
        buildSwatches(fillSwatches, true);
        buildSwatches(strokeSwatches, false);

        opacitySeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                opacityLabel.setText(pluginContext.getString(R.string.ez_fill_opacity_fmt, p));
            }
        });
        strokeWidthSeek.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                widthLabel.setText(pluginContext.getString(R.string.ez_stroke_width_fmt, strokeWidthPx()));
            }
        });

        v.findViewById(R.id.ez_btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ExclusionZone saved = save();
                if (saved != null) toast(pluginContext.getString(R.string.ez_saved_fmt, saved.label));
            }
        });
        v.findViewById(R.id.ez_btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showList();
            }
        });
        v.findViewById(R.id.ez_btn_redraw).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readForm(zone);
                pendingRedraw = zone;
                startDraw(DrawPurpose.REDRAW);
            }
        });
        v.findViewById(R.id.ez_btn_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ExclusionZone saved = save();
                if (saved == null) return;
                BasePlanItem stored = repository.find(saved.uid);
                boolean ok = stored != null && repository.share(stored);
                toast(ok ? pluginContext.getString(R.string.ez_shared_fmt, saved.label)
                        : pluginContext.getString(R.string.ez_share_failed));
            }
        });
        Button delete = v.findViewById(R.id.ez_btn_delete);
        delete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete(zone.uid, labelInput.getText().toString().trim(), true);
            }
        });
        setRoot(v);
    }

    /** Copies form values into {@code z} without validation (height kept if unparsable). */
    private boolean readForm(ExclusionZone z) {
        String label = labelInput.getText().toString().trim();
        if (!label.isEmpty()) z.label = label;
        z.remarks = remarksInput.getText().toString();
        z.fillColor = selectedFill;
        z.setStrokeColor(selectedStroke);
        z.fillOpacity = opacitySeek.getProgress() / 100f;
        z.strokeWidth = strokeWidthPx();
        z.strokeDashed = dashedCheck.isChecked();
        z.normalizeStyle();
        String h = heightInput.getText().toString().trim();
        if (h.equals(heightPrefill)) {
            z.heightAglMeters = heightOriginalMeters; // avoid re-rounding an untouched value
            return true;
        }
        double m = settings.parseDistance(h);
        if (Double.isNaN(m) || Double.isInfinite(m) || m < 0) return false;
        z.heightAglMeters = m;
        heightOriginalMeters = m;
        heightPrefill = h;
        return true;
    }

    /** Validates and stores the edited zone; returns the saved zone or null on error. */
    private ExclusionZone save() {
        ExclusionZone z = editing;
        if (z == null) return null;
        if (!readForm(z)) {
            toast(pluginContext.getString(R.string.ez_invalid_height));
            return null;
        }
        if (!readShapeParams(z)) {
            toast(pluginContext.getString(R.string.ez_invalid_shape));
            return null;
        }
        if (!z.isValid()) return null;
        z.touch();
        editing = null;
        repository.upsert(z);
        showList();
        return z;
    }

    /** R3: circle / rectangle parameter editor (hidden for free polygons). */
    private void buildShapeParams(LinearLayout box, final ExclusionZone zone) {
        centerInput = radiusInput = rectWidthInput = rectLengthInput = rotationInput = null;
        box.removeAllViews();
        if (zone.getShape() == ExclusionZoneShape.POLYGON || !zone.hasShapeParams()) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        String unit = settings.getUnitSystem().abbreviation;
        box.addView(label(pluginContext.getString(R.string.ez_shape_fmt, zone.getShape().displayName)));
        box.addView(label(pluginContext.getString(R.string.ez_center_fmt)));
        centerInput = input(String.format(java.util.Locale.US, "%.6f, %.6f",
                zone.center.lat, zone.center.lon), false);
        box.addView(centerInput);
        if (zone.getShape() == ExclusionZoneShape.CIRCLE) {
            box.addView(label(pluginContext.getString(R.string.ez_radius_fmt, unit)));
            radiusInput = input(settings.formatNumber(zone.radiusMeters), true);
            box.addView(radiusInput);
        } else {
            box.addView(label(pluginContext.getString(R.string.ez_rect_width_fmt, unit)));
            rectWidthInput = input(settings.formatNumber(zone.rectWidthMeters), true);
            box.addView(rectWidthInput);
            box.addView(label(pluginContext.getString(R.string.ez_rect_length_fmt, unit)));
            rectLengthInput = input(settings.formatNumber(zone.rectLengthMeters), true);
            box.addView(rectLengthInput);
            box.addView(label(pluginContext.getString(R.string.ez_rotation)));
            rotationInput = input(String.format(java.util.Locale.US, "%.1f", zone.rotationDeg), true);
            box.addView(rotationInput);
        }
        Button convert = new Button(pluginContext);
        convert.setText(R.string.ez_convert_polygon);
        convert.setAllCaps(false);
        convert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readForm(zone);
                readShapeParams(zone);
                zone.convertToPolygon();
                showEdit(zone, editingIsNew);
            }
        });
        box.addView(convert);
    }

    /**
     * Applies the shape inputs and regenerates the footprint. Untouched distance fields keep
     * the stored value (formatNumber round-trips may lose precision). False when invalid.
     */
    private boolean readShapeParams(ExclusionZone z) {
        if (centerInput == null || z.getShape() == ExclusionZoneShape.POLYGON) return true;
        GeoPos c = GeoPos.fromCotString(centerInput.getText().toString().replace(' ', ','
                ).replaceAll(",+", ","));
        if (c == null) return false;
        c.altHae = Double.NaN;
        if (z.getShape() == ExclusionZoneShape.CIRCLE) {
            double r = distanceOr(radiusInput, z.radiusMeters);
            if (!(r > 0)) return false;
            z.setCircle(c, r);
        } else {
            double w = distanceOr(rectWidthInput, z.rectWidthMeters);
            double l = distanceOr(rectLengthInput, z.rectLengthMeters);
            double rot;
            try {
                rot = Double.parseDouble(rotationInput.getText().toString().trim());
            } catch (NumberFormatException e) {
                return false;
            }
            if (!(w > 0) || !(l > 0) || Double.isInfinite(rot)) return false;
            z.setRectangle(c, w, l, GeoUtils.normalizeDeg(rot));
        }
        return true;
    }

    private double distanceOr(EditText in, double stored) {
        String t = in.getText().toString().trim();
        if (t.equals(settings.formatNumber(stored))) return stored;
        return settings.parseDistance(t);
    }

    private TextView label(String text) {
        TextView t = new TextView(pluginContext);
        t.setText(text);
        t.setTextColor(0xFFCCCCCC);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private EditText input(String value, boolean numeric) {
        EditText e = new EditText(pluginContext);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextColor(0xFFFFFFFF);
        if (numeric) {
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        return e;
    }

    private void confirmDelete(final String uid, final String label, final boolean fromEditor) {
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(pluginContext.getString(R.string.ez_delete_title))
                .setMessage(pluginContext.getString(R.string.ez_delete_msg_fmt, label))
                .setPositiveButton(pluginContext.getString(R.string.ez_delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (fromEditor) editing = null;
                        if (lastResult != null && lastResult.getConstrainingZoneUids().contains(uid)) {
                            lastResult = null;
                            if (overlayManager != null) overlayManager.clearInterceptResult();
                        }
                        repository.remove(uid);
                        toast(pluginContext.getString(R.string.ez_deleted_fmt, label));
                        if (fromEditor) showList();
                    }
                })
                .setNegativeButton(pluginContext.getString(R.string.ez_cancel), null)
                .show();
    }

    private void buildSwatches(final LinearLayout container, final boolean fill) {
        container.removeAllViews();
        int size = dp(30);
        for (final int c : PRESET_COLORS) {
            View sw = new View(pluginContext);
            boolean sel = (fill ? selectedFill : selectedStroke) == c;
            sw.setBackground(swatchDrawable(c, 0xFF000000, sel));
            sw.setContentDescription(String.format("#%06X", c & 0xFFFFFF));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (fill) selectedFill = c;
                    else selectedStroke = c;
                    buildSwatches(container, fill);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = dp(4);
            container.addView(sw, lp);
        }
    }

    private GradientDrawable swatchDrawable(int fill, int border, boolean selected) {
        return Swatches.drawable(pluginContext, fill, border, selected);
    }

    private int strokeWidthPx() {
        return (int) ExclusionZone.MIN_STROKE_WIDTH + strokeWidthSeek.getProgress();
    }

    // =============================================================== helpers

    static double perimeter(List<GeoPos> pts) {
        double total = 0;
        for (int i = 0; i < pts.size(); i++) {
            total += GeoUtils.distanceMeters(pts.get(i), pts.get((i + 1) % pts.size()));
        }
        return pts.size() < 2 ? 0 : total;
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
