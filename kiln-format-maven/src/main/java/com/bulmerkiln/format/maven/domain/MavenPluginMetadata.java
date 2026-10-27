/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven.domain;

import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.format.maven.util.MavenSafeXmlUtils;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the {@code <plugins>} of uploaded group-level metadata (FMT-MVN-17). Maven's deploy writes
 * them from each plugin's descriptor; everything else in an upload is ignored, since Kiln
 * generates versions itself.
 */
public final class MavenPluginMetadata {

    /** More plugins than any one group publishes. */
    static final int MAX_PLUGINS = 1000;

    private MavenPluginMetadata() {}

    /** The plugins {@code xml} lists; none for metadata without {@code <plugins>}. */
    public static List<MavenPlugin> parse(byte[] xml) {
        Document document;
        try {
            document = MavenSafeXmlUtils.parse(xml);
        } catch (MavenSafeXmlUtils.InvalidXmlException e) {
            throw invalid("Metadata must be well-formed XML");
        }
        Element root = document.getDocumentElement();
        if (!"metadata".equals(root.getLocalName())) {
            throw invalid("Metadata must be a <metadata> document");
        }
        List<MavenPlugin> plugins = new ArrayList<>();
        for (Element section : children(root, "plugins")) {
            for (Element plugin : children(section, "plugin")) {
                if (plugins.size() == MAX_PLUGINS) {
                    throw invalid("Metadata lists too many plugins");
                }
                plugins.add(plugin(plugin));
            }
        }
        return plugins;
    }

    private static MavenPlugin plugin(Element plugin) {
        String artifactId = text(plugin, "artifactId");
        String prefix = text(plugin, "prefix");
        if (artifactId == null || prefix == null) {
            throw invalid("A plugin needs an artifactId and a prefix");
        }
        return new MavenPlugin(artifactId, prefix, text(plugin, "name"));
    }

    private static @Nullable String text(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.getFirst().getTextContent().strip();
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static RefusedException invalid(String message) {
        return new RefusedException(Refusal.of(Reason.INVALID, message));
    }
}
