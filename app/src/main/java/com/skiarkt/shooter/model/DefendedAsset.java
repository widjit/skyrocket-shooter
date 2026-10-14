package com.skiarkt.shooter.model;

import android.os.Parcel;

/** Area / facility being protected. {@link #color} mirrors {@link #borderColor}. */
public class DefendedAsset extends PolygonItem {
    public int fillColor = PlanColors.DEFENDED_FILL;
    public int borderColor = PlanColors.DEFENDED_BORDER;
    /** 1 = highest priority. Used by best-effector designation and PACE planning. */
    public int priority = 1;

    public DefendedAsset() {
        super("Defended Asset", PlanColors.DEFENDED_BORDER);
    }

    protected DefendedAsset(Parcel in) {
        super(in);
        fillColor = in.readInt();
        borderColor = in.readInt();
        priority = in.readInt();
    }

    @Override
    public ItemKind getKind() {
        return ItemKind.DEFENDED_ASSET;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writePolygon(dest);
        dest.writeInt(fillColor);
        dest.writeInt(borderColor);
        dest.writeInt(priority);
    }

    public static final Creator<DefendedAsset> CREATOR = new Creator<DefendedAsset>() {
        @Override
        public DefendedAsset createFromParcel(Parcel in) {
            return new DefendedAsset(in);
        }

        @Override
        public DefendedAsset[] newArray(int size) {
            return new DefendedAsset[size];
        }
    };
}
