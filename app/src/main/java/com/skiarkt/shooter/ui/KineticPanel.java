package com.skiarkt.shooter.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.R;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.AzimuthArc;
import com.skiarkt.shooter.model.DetonationMode;
import com.skiarkt.shooter.model.EffectorStatus;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.KineticEffectorType;
import com.skiarkt.shooter.model.MunitionType;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kinetic effectors section (Phase 4): rifles, shotguns, net guns, rockets, laser-guided
 * rockets and drone interceptors with min/max range rings, sector limits, restricted
 * azimuths and fragmentation heat bands (rendered by KineticEffectorOverlay).
 */
public class KineticPanel extends PointItemPanel<KineticEffector> {

    private KineticEffectorType selType;
    private MunitionType selMunition;
    private DetonationMode selDetonation;
    private EffectorStatus selStatus;
    private DistanceField minRangeField;
    private EditText azMinInput;
    private EditText azMaxInput;
    private EditText elMinInput;
    private EditText elMaxInput;
    private DistanceField fragRadiusField;
    private DistanceField fragWidthField;
    private EditText velocityInput;
    private EditText restrictedInput;
    private DistanceField minInterceptField;
    private EditText perimeterInput;
    private CheckBox ffCheck;
    private EditText sensorsInput;
    private EditText designatorsInput;
    private EditText laserCodeInput;
    private EditText ammoInput;
    private EditText reloadInput;
    private EditText notesInput;

    public KineticPanel(Context pluginContext, MapView mapView, SettingsManager settings,
            PointDefenseRepository repository, OverlayManager overlayManager, Runnable onBack) {
        super(pluginContext, mapView, settings, repository, overlayManager, onBack);
    }

    @Override
    ItemKind kind() {
        return ItemKind.KINETIC_EFFECTOR;
    }

    @Override
    int sectionTitleRes() {
        return R.string.section_kinetic;
    }

    @Override
    int newButtonRes() {
        return R.string.ke_new;
    }

    @Override
    int listHeaderRes() {
        return R.string.ke_list_header;
    }

    @Override
    int listEmptyRes() {
        return R.string.ke_list_empty;
    }

    @Override
    int editTitleRes(boolean isNew) {
        return isNew ? R.string.ke_edit_title_new : R.string.ke_edit_title;
    }

    @Override
    KineticEffector createItem(GeoPos position) {
        KineticEffector k = new KineticEffector();
        k.position = position;
        k.label = "Kinetic " + (repository.getItems(ItemKind.KINETIC_EFFECTOR).size() + 1);
        return k;
    }

    @Override
    KineticEffector copyOf(KineticEffector item) {
        return item.copy();
    }

    @Override
    String listText(KineticEffector k) {
        k.ensureLists();
        return ctx.getString(R.string.ke_list_item_fmt, k.label, k.type.displayName,
                settings.formatDistance(k.minEngagementRange), settings.formatDistance(k.maxEngagementDistMeters));
    }

    @Override
    void buildSpecificFields(LinearLayout form, KineticEffector item) {
        item.ensureLists();
        String unit = settings.getUnitSystem().abbreviation;
        selType = item.type;
        selMunition = item.munitionType;
        selDetonation = item.detonationMode;
        selStatus = item.status;

        final Button typeBtn = button(ctx.getString(R.string.ke_type_fmt, selType.displayName));
        typeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KineticEffectorType prev = selType;
                selType = selType.next();
                typeBtn.setText(ctx.getString(R.string.ke_type_fmt, selType.displayName));
                // Lasers default to the speed of light; untouched defaults follow the type.
                if (velocityInput != null) {
                    double cur = parseNumber(velocityInput.getText().toString());
                    double next = KineticEffector.velocityForTypeChange(prev, selType,
                            Double.isNaN(cur) ? -1 : cur);
                    if (Double.isNaN(cur) || Double.compare(next, cur) != 0) {
                        velocityInput.setText(String.format(Locale.US, "%.1f", next));
                    }
                }
            }
        });
        form.addView(typeBtn, matchWrap());

        minRangeField = distanceField(form, ctx.getString(R.string.ke_min_range_fmt, unit), item.minEngagementRange);
        azMinInput = field(form, ctx.getString(R.string.ke_az_min), decimal(false), fmtBoxed(item.azimuthMin));
        azMaxInput = field(form, ctx.getString(R.string.ke_az_max), decimal(false), fmtBoxed(item.azimuthMax));
        elMinInput = numberField(form, ctx.getString(R.string.ke_el_min), item.elevationMin, true);
        elMaxInput = numberField(form, ctx.getString(R.string.ke_el_max), item.elevationMax, true);
        restrictedInput = field(form, ctx.getString(R.string.ke_restricted), InputType.TYPE_CLASS_TEXT,
                arcsText(item.restrictedAzimuths));

        fragRadiusField = distanceField(form, ctx.getString(R.string.ke_frag_radius_fmt, unit),
                item.fragmentationRadiusMeters);
        fragWidthField = distanceField(form, ctx.getString(R.string.ke_frag_width_fmt, unit),
                item.fragmentationRouteWidth);

        final Button munBtn = button(ctx.getString(R.string.ke_munition_fmt, selMunition.displayName));
        munBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selMunition = selMunition.next();
                munBtn.setText(ctx.getString(R.string.ke_munition_fmt, selMunition.displayName));
            }
        });
        form.addView(munBtn, matchWrap());
        final Button detBtn = button(ctx.getString(R.string.ke_detonation_fmt, selDetonation.displayName));
        detBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selDetonation = selDetonation.next();
                detBtn.setText(ctx.getString(R.string.ke_detonation_fmt, selDetonation.displayName));
            }
        });
        form.addView(detBtn, matchWrap());
        velocityInput = numberField(form, ctx.getString(R.string.ke_velocity), item.munitionSpeedMps, false);

        minInterceptField = distanceField(form, ctx.getString(R.string.ke_min_intercept_fmt, unit),
                item.minInterceptAltAGL);
        perimeterInput = field(form, ctx.getString(R.string.ke_perimeter_uid), InputType.TYPE_CLASS_TEXT,
                item.engagementAuthorityPerimeterUid);
        ffCheck = new CheckBox(ctx);
        ffCheck.setText(ctx.getString(R.string.ke_ff_check));
        ffCheck.setTextColor(0xFFFFFFFF);
        ffCheck.setChecked(item.friendlyFireCheckEnabled);
        form.addView(ffCheck, matchWrap());
        sensorsInput = field(form, ctx.getString(R.string.ke_sensors), InputType.TYPE_CLASS_TEXT,
                joinList(item.linkedSensorUids));
        designatorsInput = field(form, ctx.getString(R.string.ke_designators), InputType.TYPE_CLASS_TEXT,
                joinList(item.linkedLaserDesignatorUids));
        laserCodeInput = field(form, ctx.getString(R.string.ke_laser_code), InputType.TYPE_CLASS_NUMBER,
                item.laserCode == null ? "" : String.valueOf(item.laserCode));

        final Button statusBtn = button(ctx.getString(R.string.ke_status_fmt, selStatus.displayName));
        statusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selStatus = selStatus.next();
                statusBtn.setText(ctx.getString(R.string.ke_status_fmt, selStatus.displayName));
            }
        });
        form.addView(statusBtn, matchWrap());
        ammoInput = field(form, ctx.getString(R.string.ke_ammo), InputType.TYPE_CLASS_NUMBER,
                String.valueOf(item.ammoCount));
        reloadInput = numberField(form, ctx.getString(R.string.ke_reload), item.reloadTimeSec, false);
        notesInput = field(form, ctx.getString(R.string.ke_notes),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, item.notes);
    }

    @Override
    String readSpecificFields(KineticEffector item) {
        String err = null;
        item.type = selType;
        item.munitionType = selMunition;
        item.detonationMode = selDetonation;
        item.status = selStatus;

        double minR = minRangeField.read();
        if (Double.isNaN(minR) || minR < 0) err = ctx.getString(R.string.ke_min_range_fmt,
                settings.getUnitSystem().abbreviation);
        else item.minEngagementRange = minR;

        String azMin = azMinInput.getText().toString().trim();
        String azMax = azMaxInput.getText().toString().trim();
        if (azMin.isEmpty() || azMax.isEmpty()) {
            item.azimuthMin = null;
            item.azimuthMax = null;
        } else {
            double a0 = parseNumber(azMin), a1 = parseNumber(azMax);
            if (Double.isNaN(a0) || a0 < 0 || a0 > 360) err = orFirst(err, R.string.ke_az_min);
            else if (Double.isNaN(a1) || a1 < 0 || a1 > 360) err = orFirst(err, R.string.ke_az_max);
            else {
                item.azimuthMin = a0;
                item.azimuthMax = a1;
            }
        }
        double e0 = parseNumber(elMinInput.getText().toString());
        double e1 = parseNumber(elMaxInput.getText().toString());
        if (Double.isNaN(e0) || Double.isNaN(e1) || e0 >= e1) err = orFirst(err, R.string.ke_el_min);
        else {
            item.elevationMin = e0;
            item.elevationMax = e1;
        }

        List<AzimuthArc> arcs = new ArrayList<>();
        for (String part : restrictedInput.getText().toString().split(";")) {
            if (part.trim().isEmpty()) continue;
            AzimuthArc a = AzimuthArc.parse(part);
            if (a == null) {
                err = orFirst(err, R.string.ke_restricted);
                arcs = null;
                break;
            }
            arcs.add(a);
        }
        if (arcs != null) item.restrictedAzimuths = arcs;

        double fr = fragRadiusField.read();
        if (Double.isNaN(fr) || fr < 0) err = err != null ? err : ctx.getString(R.string.ke_frag_radius_fmt,
                settings.getUnitSystem().abbreviation);
        else item.fragmentationRadiusMeters = fr;
        double fw = fragWidthField.read();
        if (Double.isNaN(fw) || fw < 0) err = err != null ? err : ctx.getString(R.string.ke_frag_width_fmt,
                settings.getUnitSystem().abbreviation);
        else item.fragmentationRouteWidth = fw;
        double vel = parseNumber(velocityInput.getText().toString());
        if (Double.isNaN(vel) || vel <= 0) err = orFirst(err, R.string.ke_velocity);
        else item.munitionSpeedMps = vel;

        double mia = minInterceptField.read();
        if (Double.isNaN(mia) || mia < 0) err = err != null ? err : ctx.getString(R.string.ke_min_intercept_fmt,
                settings.getUnitSystem().abbreviation);
        else item.minInterceptAltAGL = mia;
        String per = perimeterInput.getText().toString().trim();
        item.engagementAuthorityPerimeterUid = per.isEmpty() ? null : per;
        item.friendlyFireCheckEnabled = ffCheck.isChecked();
        item.linkedSensorUids = splitList(sensorsInput.getText().toString());
        item.linkedLaserDesignatorUids = splitList(designatorsInput.getText().toString());

        String lc = laserCodeInput.getText().toString().trim();
        if (lc.isEmpty()) {
            item.laserCode = null;
            if (selType == KineticEffectorType.LASER_GUIDED_ROCKET) err = orFirst(err, R.string.ke_laser_code);
        } else {
            Integer code = null;
            try {
                code = Integer.parseInt(lc);
            } catch (NumberFormatException ignored) {
                code = null;
            }
            if (!KineticEffector.isValidLaserCode(code)) err = orFirst(err, R.string.ke_laser_code);
            else item.laserCode = code;
        }

        double ammo = parseNumber(ammoInput.getText().toString());
        if (Double.isNaN(ammo) || ammo < 0 || ammo != Math.floor(ammo)) err = orFirst(err, R.string.ke_ammo);
        else item.ammoCount = (int) ammo;
        double rl = parseNumber(reloadInput.getText().toString());
        if (Double.isNaN(rl) || rl < 0) err = orFirst(err, R.string.ke_reload);
        else item.reloadTimeSec = rl;
        item.notes = notesInput.getText().toString();

        if (err == null && item.validationError() != null) err = item.validationError();
        return err;
    }

    @Override
    String statsText(KineticEffector k) {
        if (!k.isValid()) return null;
        return ctx.getString(R.string.ke_stats_fmt, settings.formatDistance(k.minEngagementRange),
                settings.formatDistance(k.maxEngagementDistMeters), k.azimuthSweepDeg(),
                k.restrictedAzimuths.size(), k.timeOfFlightSec(k.maxEngagementDistMeters));
    }

    // ------------------------------------------------------------------ helpers

    private String orFirst(String err, int labelRes) {
        return err != null ? err : ctx.getString(labelRes);
    }

    private static int decimal(boolean signed) {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0);
    }

    private static String fmtBoxed(Double v) {
        return v == null ? "" : String.format(Locale.US, "%.1f", v);
    }

    static String arcsText(List<AzimuthArc> arcs) {
        StringBuilder sb = new StringBuilder();
        if (arcs != null) {
            for (AzimuthArc a : arcs) {
                if (a == null) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append(String.format(Locale.US, "%s-%s", trim(a.bearingMin), trim(a.bearingMax)));
            }
        }
        return sb.toString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String joinList(List<String> l) {
        StringBuilder sb = new StringBuilder();
        if (l != null) {
            for (String s : l) {
                if (s == null || s.isEmpty()) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(s);
            }
        }
        return sb.toString();
    }

    private static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
