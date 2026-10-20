package com.btlrckt.bottlerocket.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** Shared colour-swatch drawable used by the item, exclusion-zone and authority panels. */
final class Swatches {

    /** Highlight colour for the selected swatch border. */
    static final int SELECTED_BORDER = 0xFFFFC107;

    private Swatches() {}

    /** Rounded swatch: 3 dp amber border when selected, otherwise a 1 dp {@code border}. */
    static GradientDrawable drawable(Context ctx, int fill, int border, boolean selected) {
        float density = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(Math.round(4 * density));
        d.setStroke(Math.round((selected ? 3 : 1) * density), selected ? SELECTED_BORDER : border);
        return d;
    }
}
