/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.dropdown;

public class DropDown {
    public interface OnStateListener {
        void onDropDownSelectionRemoved();

        void onDropDownClose();

        void onDropDownSizeChanged(double width, double height);

        void onDropDownVisible(boolean v);
    }
}
