package com.btlrckt.bottlerocket.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Guards assets/plugin.xml against the shape ATAK's strict SimpleXML parser rejects.
 * ATAK 5.6 maps it onto {@code @Root(name="plugin")} with only an inline
 * {@code <extension type impl [singleton]>} list; anything else fails the parse and the APK
 * is silently not treated as a plugin.
 */
public class PluginDescriptorTest {
    private static Element root() throws Exception {
        File f = new File("src/main/assets/plugin.xml");
        if (!f.exists()) f = new File("app/src/main/assets/plugin.xml");
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getDocumentElement();
    }

    @Test
    public void rootIsPluginWithNoAttributes() throws Exception {
        Element root = root();
        assertEquals("plugin", root.getTagName());
        assertEquals("<plugin> must have no attributes", 0, root.getAttributes().getLength());
    }

    @Test
    public void singleIPluginExtensionWithOnlyKnownAttributes() throws Exception {
        NodeList children = root().getChildNodes();
        int extensions = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            assertEquals("only <extension> children allowed", "extension", e.getTagName());
            NamedNodeMap attrs = e.getAttributes();
            for (int a = 0; a < attrs.getLength(); a++) {
                String name = attrs.item(a).getNodeName();
                assertTrue("unknown extension attribute: " + name,
                        name.equals("type") || name.equals("impl") || name.equals("singleton"));
            }
            assertEquals("gov.tak.api.plugin.IPlugin", e.getAttribute("type"));
            assertEquals(PluginLifecycle.class.getName(), e.getAttribute("impl"));
            extensions++;
        }
        assertEquals(1, extensions);
    }
}
