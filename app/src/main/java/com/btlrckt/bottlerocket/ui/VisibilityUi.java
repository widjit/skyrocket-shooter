package com.btlrckt.bottlerocket.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.btlrckt.bottlerocket.R;
import com.btlrckt.bottlerocket.data.PointDefenseRepository;
import com.btlrckt.bottlerocket.data.VisibilityStore;

/**
 * R9 shared list-row helpers for the panels that do not extend {@link PointItemPanel}
 * (exclusion zones, authority zones). Visibility is a local view setting only.
 */
final class VisibilityUi {
    private VisibilityUi() {
    }

    /** Hide/Show button: tap toggles, long-press solos (hides every other plan item). */
    static Button eye(final Context pluginCtx, final Context toastCtx, final PointDefenseRepository repo,
            final String uid, final String label) {
        final VisibilityStore vis = repo.getVisibility();
        Button b = new Button(pluginCtx);
        b.setAllCaps(false);
        b.setText(pluginCtx.getString(vis.isHidden(uid) ? R.string.vis_show : R.string.vis_hide));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vis.toggle(uid);
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean solo = vis.toggleSolo(uid, PointItemPanel.allPlanUids(repo));
                Toast.makeText(toastCtx, solo ? pluginCtx.getString(R.string.vis_solo_fmt, label)
                        : pluginCtx.getString(R.string.vis_solo_end), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        return b;
    }

    /** Row label with the " (hidden)" suffix when hidden. */
    static String label(Context pluginCtx, PointDefenseRepository repo, String uid, String text) {
        return repo.getVisibility().isHidden(uid) ? text + pluginCtx.getString(R.string.vis_hidden_suffix) : text;
    }

    static float alpha(PointDefenseRepository repo, String uid) {
        return repo.getVisibility().isHidden(uid) ? 0.5f : 1f;
    }
}
