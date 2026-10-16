/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.cot.event;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Map;

public class CotEvent {
    private String uid;
    private String type;
    private String version = "2.0";
    private String how;
    private CoordinatedTime time;
    private CoordinatedTime start;
    private CoordinatedTime stale;
    private CotPoint point;
    private CotDetail detail;

    public static CotEvent parse(String xml) {
        throw new UnsupportedOperationException("CotEvent.parse requires the ATAK runtime");
    }

    public String getUID() { return uid; }
    public void setUID(String uid) { this.uid = uid; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getHow() { return how; }
    public void setHow(String how) { this.how = how; }
    public CoordinatedTime getTime() { return time; }
    public void setTime(CoordinatedTime time) { this.time = time; }
    public CoordinatedTime getStart() { return start; }
    public void setStart(CoordinatedTime start) { this.start = start; }
    public CoordinatedTime getStale() { return stale; }
    public void setStale(CoordinatedTime stale) { this.stale = stale; }
    public CotPoint getCotPoint() { return point; }
    public void setPoint(CotPoint point) { this.point = point; }
    public CotDetail getDetail() { return detail; }
    public void setDetail(CotDetail detail) { this.detail = detail; }

    public boolean isValid() {
        return uid != null && type != null && point != null && time != null && stale != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<event version='").append(version).append("' uid='").append(esc(uid))
                .append("' type='").append(esc(type)).append("' how='").append(esc(how)).append("'>");
        if (point != null) {
            sb.append("<point lat='").append(point.getLat()).append("' lon='").append(point.getLon())
                    .append("' hae='").append(point.getHae()).append("' ce='").append(point.getCe())
                    .append("' le='").append(point.getLe()).append("'/>");
        }
        if (detail != null) {
            append(sb, detail);
        }
        return sb.append("</event>").toString();
    }

    private static void append(StringBuilder sb, CotDetail d) {
        sb.append('<').append(d.getElementName());
        for (Map.Entry<String, String> e : d.attributeMap().entrySet()) {
            sb.append(' ').append(e.getKey()).append("='").append(esc(e.getValue())).append('\'');
        }
        sb.append('>');
        if (d.getInnerText() != null) {
            sb.append(esc(d.getInnerText()));
        }
        for (CotDetail c : d.getChildren()) {
            append(sb, c);
        }
        sb.append("</").append(d.getElementName()).append('>');
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&apos;").replace("\"", "&quot;");
    }
}
