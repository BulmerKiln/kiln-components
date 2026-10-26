/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.grype;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanJob;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerId;
import com.bulmerkiln.scan.Severities;
import com.bulmerkiln.scan.ToolOutput;
import com.bulmerkiln.scan.ToolRun;
import com.bulmerkiln.scan.ToolText;
import com.bulmerkiln.scan.ToolVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Grype, matching the stored SBOM. Reads {@code matches[]}, with CVE aliases
 * in {@code relatedVulnerabilities}. The worker sets {@code GRYPE_DB_AUTO_UPDATE=false} and points
 * it at the mirrored database.
 */
public final class GrypeAdapter implements ScannerAdapter {

    public static final ScannerId ID = new ScannerId("grype");

    private static final ToolVersion MINIMUM = new ToolVersion(0, 121, 0);

    private static final int MAX_MINOR = 121;

    @Override
    public ScannerId scanner() {
        return ID;
    }

    @Override
    public boolean supportsVersion(String toolVersion) {
        return ToolVersion.supported(toolVersion, MINIMUM, MAX_MINOR);
    }

    @Override
    public List<String> command(ScanJob job, Path input) {
        return List.of("/usr/local/bin/grype", "sbom:" + input, "-o", "json", "-q");
    }

    @Override
    public List<Finding> parse(ScanJob job, ToolRun run) {
        JsonNode root = ToolOutput.json(run.output());
        JsonNode matches = root.path("matches");
        if (!root.isObject()
                || !matches.isArray()
                || !"grype".equals(root.path("descriptor").path("name").asString(""))) {
            throw new IllegalArgumentException("Not a Grype report");
        }
        List<Finding> findings = new ArrayList<>();
        for (JsonNode match : matches) {
            findings.add(finding(match));
        }
        return findings;
    }

    private static Finding finding(JsonNode match) {
        JsonNode vulnerability = match.required("vulnerability");
        String id = vulnerability.required("id").asString();
        Set<String> aliases = new LinkedHashSet<>();
        for (JsonNode related : list(match.path("relatedVulnerabilities"))) {
            String alias = related.path("id").asString("");
            if (!alias.isEmpty() && !alias.equals(id)) {
                aliases.add(alias);
            }
        }
        return new Finding(
                Finding.Kind.VULNERABILITY,
                id,
                List.copyOf(aliases),
                purl(match.path("artifact")),
                Severities.named(vulnerability.path("severity").asString(null)),
                fixedVersion(vulnerability.path("fix")),
                null);
    }

    private static @Nullable String fixedVersion(JsonNode fix) {
        if (!"fixed".equals(fix.path("state").asString(""))) {
            return null;
        }
        for (JsonNode version : list(fix.path("versions"))) {
            if (version.isString()) {
                return ToolText.clean(version.asString(), 255);
            }
        }
        return null;
    }

    private static @Nullable String purl(JsonNode artifact) {
        JsonNode purl = artifact.path("purl");
        return purl.isString() && purl.asString().startsWith("pkg:") ? purl.asString() : null;
    }

    private static Iterable<JsonNode> list(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected a list");
        }
        return node;
    }
}
