/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package gov.tak.api.ui;

import gov.tak.api.commons.graphics.Bitmap;

public class ToolbarItem {
    public static class Builder {
        public Builder(String title, Bitmap icon) {
        }

        public Builder setListener(IToolbarItemListener listener) {
            return this;
        }

        public Builder setIdentifier(String identifier) {
            return this;
        }

        public ToolbarItem build() {
            return new ToolbarItem();
        }
    }
}
