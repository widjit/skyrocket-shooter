/* Compile-only stub mirroring the CivTAK 4.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.cot.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CotDetail {
    private String elementName;
    private String innerText;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<CotDetail> children = new ArrayList<>();

    public CotDetail() {
        this("detail");
    }

    public CotDetail(String elementName) {
        this.elementName = elementName;
    }

    public String getElementName() {
        return elementName;
    }

    public void setElementName(String name) {
        this.elementName = name;
    }

    public String getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    public String getInnerText() {
        return innerText;
    }

    public void setInnerText(String text) {
        this.innerText = text;
    }

    public void addChild(CotDetail child) {
        children.add(child);
    }

    public List<CotDetail> getChildren() {
        return new ArrayList<>(children);
    }

    public int childCount() {
        return children.size();
    }

    /** Package helper used by the stub CotEvent.toString(). */
    Map<String, String> attributeMap() {
        return attributes;
    }
}
