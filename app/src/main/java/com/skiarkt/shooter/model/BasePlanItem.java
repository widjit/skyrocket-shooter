package com.skiarkt.shooter.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

/** Common fields for every object placed in a point defense plan. */
public abstract class BasePlanItem implements Parcelable {
    public String uid;
    public String label;
    /** Primary ARGB display color. */
    public int color;
    public String remarks = "";
    public long createdMillis;
    public long modifiedMillis;

    protected BasePlanItem(String labelPrefix, int defaultColor) {
        this.uid = "skiarkt-" + getKindCode() + "-" + UUID.randomUUID();
        this.label = labelPrefix;
        this.color = defaultColor;
        this.createdMillis = System.currentTimeMillis();
        this.modifiedMillis = createdMillis;
    }

    protected BasePlanItem(Parcel in) {
        uid = in.readString();
        label = in.readString();
        color = in.readInt();
        remarks = in.readString();
        createdMillis = in.readLong();
        modifiedMillis = in.readLong();
    }

    public abstract ItemKind getKind();

    /** Representative point (marker position / CoT point). Centroid for polygons. */
    public abstract GeoPos getAnchor();

    /** True when the item has enough geometry to render and analyse. */
    public abstract boolean isValid();

    private String getKindCode() {
        ItemKind k = getKind();
        return k == null ? "item" : k.code;
    }

    public void touch() {
        modifiedMillis = System.currentTimeMillis();
    }

    protected void writeBase(Parcel dest) {
        dest.writeString(uid);
        dest.writeString(label);
        dest.writeInt(color);
        dest.writeString(remarks);
        dest.writeLong(createdMillis);
        dest.writeLong(modifiedMillis);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return getKind().displayName + "[" + label + ", " + uid + "]";
    }
}
