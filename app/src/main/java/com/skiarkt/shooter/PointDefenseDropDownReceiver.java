package com.skiarkt.shooter;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.ui.ExclusionZonePanel;
import com.skiarkt.shooter.ui.FeatureSection;
import com.skiarkt.shooter.ui.NonKineticPanel;
import com.skiarkt.shooter.ui.SensorPanel;
import com.skiarkt.shooter.ui.SettingsPanel;
import com.skiarkt.shooter.util.DtedHelper;

import java.io.File;
import java.io.IOException;

/**
 * Main side panel. Shows the plan header, a navigation menu for every feature area and
 * swaps section views in-place (with Back navigation). Feature sections are stubs
 * until their roadmap phase; Settings is fully functional.
 */
public class PointDefenseDropDownReceiver extends DropDownReceiver
        implements DropDown.OnStateListener, PointDefenseRepository.PlanChangeListener {

    public static final String SHOW_PLUGIN = "com.skiarkt.shooter.SHOW_PLUGIN";
    /** Optional intent extra: open directly on a {@link FeatureSection} name. */
    public static final String EXTRA_SECTION = "section";

    private final Context pluginContext;
    private final SettingsManager settings;
    private final PointDefenseRepository repository;
    private final DtedHelper dtedHelper;
    private final OverlayManager overlayManager;
    private ExclusionZonePanel exclusionZonePanel;
    private SensorPanel sensorPanel;
    private NonKineticPanel nonKineticPanel;

    private final View rootView;
    private final TextView planSummary;
    private final ViewGroup contentContainer;
    private FeatureSection currentSection;

    public PointDefenseDropDownReceiver(MapView mapView, Context pluginContext, SettingsManager settings,
            PointDefenseRepository repository, DtedHelper dtedHelper, OverlayManager overlayManager) {
        super(mapView);
        this.overlayManager = overlayManager;
        this.pluginContext = pluginContext;
        this.settings = settings;
        this.repository = repository;
        this.dtedHelper = dtedHelper;

        rootView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
        planSummary = rootView.findViewById(R.id.plan_summary);
        contentContainer = rootView.findViewById(R.id.content_container);
        repository.addListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SHOW_PLUGIN.equals(intent.getAction())) return;
        FeatureSection target = null;
        String sectionName = intent.getStringExtra(EXTRA_SECTION);
        if (sectionName != null) {
            try {
                target = FeatureSection.valueOf(sectionName);
            } catch (IllegalArgumentException ignored) {
                // fall through to main menu
            }
        }
        if (target != null) showSection(target);
        else showMainMenu();

        if (isClosed()) {
            showDropDown(rootView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
        }
    }

    // ------------------------------------------------------------ navigation

    private void showMainMenu() {
        hidePanels(null);
        currentSection = null;
        updatePlanSummary();
        View menu = PluginLayoutInflater.inflate(pluginContext, R.layout.main_menu, null);
        LinearLayout list = menu.findViewById(R.id.section_list);
        for (final FeatureSection section : FeatureSection.values()) {
            Button b = new Button(pluginContext);
            b.setText(menuLabel(section));
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSection(section);
                }
            });
            list.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        menu.findViewById(R.id.btn_save_plan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePlan();
            }
        });
        menu.findViewById(R.id.btn_share_plan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharePlan();
            }
        });
        menu.findViewById(R.id.btn_new_plan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                repository.newPlan(null, settings.getDefaultAuthor(), settings.getDefaultClassification());
                toast(pluginContext.getString(R.string.msg_new_plan));
            }
        });
        setContent(menu);
    }

    private String menuLabel(FeatureSection section) {
        String title = pluginContext.getString(section.titleRes);
        if (section.kind != null) {
            return title + " (" + repository.getPlan().count(section.kind) + ")";
        }
        return title;
    }

    private void showSection(FeatureSection section) {
        currentSection = section;
        updatePlanSummary();
        hidePanels(section);
        View view;
        if (section == FeatureSection.EXCLUSION_ZONES) {
            if (exclusionZonePanel == null) {
                exclusionZonePanel = new ExclusionZonePanel(pluginContext, getMapView(), settings, repository,
                        dtedHelper, overlayManager, new Runnable() {
                            @Override
                            public void run() {
                                showMainMenu();
                            }
                        });
            }
            view = exclusionZonePanel.createView();
        } else if (section == FeatureSection.SENSORS) {
            if (sensorPanel == null) {
                sensorPanel = new SensorPanel(pluginContext, getMapView(), settings, repository, overlayManager,
                        backToMenu());
            }
            view = sensorPanel.createView();
        } else if (section == FeatureSection.NON_KINETIC) {
            if (nonKineticPanel == null) {
                nonKineticPanel = new NonKineticPanel(pluginContext, getMapView(), settings, repository,
                        overlayManager, backToMenu());
            }
            view = nonKineticPanel.createView();
        } else if (section == FeatureSection.SETTINGS) {
            view = new SettingsPanel(pluginContext, settings, repository, new Runnable() {
                @Override
                public void run() {
                    showMainMenu();
                }
            }).createView();
        } else {
            view = createPlaceholder(section);
        }
        setContent(view);
    }

    private Runnable backToMenu() {
        return new Runnable() {
            @Override
            public void run() {
                showMainMenu();
            }
        };
    }

    /** Leaves placement / draw modes of every panel except the one for {@code keep}. */
    private void hidePanels(FeatureSection keep) {
        if (keep != FeatureSection.EXCLUSION_ZONES && exclusionZonePanel != null) exclusionZonePanel.onHidden();
        if (keep != FeatureSection.SENSORS && sensorPanel != null) sensorPanel.onHidden();
        if (keep != FeatureSection.NON_KINETIC && nonKineticPanel != null) nonKineticPanel.onHidden();
    }

    private View createPlaceholder(FeatureSection section) {
        View v = PluginLayoutInflater.inflate(pluginContext, R.layout.section_placeholder, null);
        ((TextView) v.findViewById(R.id.section_title)).setText(section.titleRes);
        ((TextView) v.findViewById(R.id.section_description)).setText(section.descriptionRes);
        ((TextView) v.findViewById(R.id.section_phase)).setText(
                pluginContext.getString(R.string.section_phase_fmt, section.phase));
        TextView items = v.findViewById(R.id.section_items);
        items.setText(describeItems(section.kind));
        v.findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMainMenu();
            }
        });
        return v;
    }

    private String describeItems(ItemKind kind) {
        if (kind == null) {
            return pluginContext.getString(R.string.section_analysis_summary,
                    repository.getPlan().totalItems());
        }
        StringBuilder sb = new StringBuilder();
        for (BasePlanItem item : repository.getItems(kind)) {
            sb.append("\u2022 ").append(item.label);
            if (item.getAnchor() != null) {
                double elev = dtedHelper.getElevationHae(item.getAnchor());
                sb.append("  [terrain ").append(settings.formatDistance(elev)).append(" HAE]");
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? pluginContext.getString(R.string.section_no_items) : sb.toString();
    }

    private void setContent(View v) {
        contentContainer.removeAllViews();
        contentContainer.addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updatePlanSummary() {
        PointDefensePlan plan = repository.getPlan();
        planSummary.setText(pluginContext.getString(R.string.plan_summary_fmt,
                plan.name, plan.classification, plan.totalItems()));
    }

    // --------------------------------------------------------------- actions

    private void savePlan() {
        File file = repository.defaultPlanFile();
        if (file == null) {
            toast(pluginContext.getString(R.string.msg_no_storage));
            return;
        }
        try {
            repository.saveToFile(file);
            toast(pluginContext.getString(R.string.msg_saved, file.getName()));
        } catch (IOException e) {
            toast(pluginContext.getString(R.string.msg_save_failed, e.getMessage()));
        }
    }

    private void sharePlan() {
        int sent = repository.sharePlan();
        toast(pluginContext.getString(R.string.msg_shared, sent));
    }

    private void toast(String msg) {
        Toast.makeText(getMapView().getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------- callbacks

    @Override
    public void onPlanChanged(PointDefenseRepository.ChangeType type, BasePlanItem item) {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (currentSection == null) showMainMenu();
                else if (currentSection == FeatureSection.EXCLUSION_ZONES) {
                    // Panel refreshes itself and never clobbers an open edit form / draw session.
                    updatePlanSummary();
                    if (exclusionZonePanel != null) exclusionZonePanel.refresh();
                } else if (currentSection == FeatureSection.SENSORS) {
                    updatePlanSummary();
                    if (sensorPanel != null) sensorPanel.refresh();
                } else if (currentSection == FeatureSection.NON_KINETIC) {
                    updatePlanSummary();
                    if (nonKineticPanel != null) nonKineticPanel.refresh();
                } else if (currentSection != FeatureSection.SETTINGS) showSection(currentSection);
                else updatePlanSummary();
            }
        });
    }

    @Override
    protected void disposeImpl() {
        repository.removeListener(this);
        if (exclusionZonePanel != null) exclusionZonePanel.dispose();
        if (sensorPanel != null) sensorPanel.dispose();
        if (nonKineticPanel != null) nonKineticPanel.dispose();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        // Leave draw mode so map taps regain their default behaviour.
        hidePanels(null);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) updatePlanSummary();
    }
}
