/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package gov.tak.api.ui;

import gov.tak.platform.ui.MotionEvent;

public interface IToolbarItemListener {
    void onItemEvent(ToolbarItem item, MotionEvent event);
}
