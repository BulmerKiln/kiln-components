/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.trivy;

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
 * Trivy, matching the stored SBOM for OS and language-package vulnerabilities. Reads report schema
 * version 2: {@code Results[].Vulnerabilities[]}, with GHSA aliases in {@code VendorIDs}. Offline:
 * the worker provides the database from the mirror.
 */
public final class TrivyAdapter implements ScannerAdapter {

    public static final ScannerId ID = new ScannerId("trivy");

    /** 0.x minors may change the report, so each needs its own recording. */
    private static final ToolVersion MINIMUM = new ToolVersion(0, 76, 0);

    private static final int MAX_MINOR = 76;

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
        return List.of(
                "/usr/local/bin/trivy",
                "sbom",
                "--format",
                "json",
                "--quiet",
                "--offline-scan",
                "--skip-db-update",
                "--skip-java-db-update",
                input.toString());
    }

    @Override
    public List<Finding> parse(ScanJob job, ToolRun run) {
        JsonNode root = ToolOutput.json(run.output());
        if (!root.isObject() || root.path("SchemaVersion").asInt(0) != 2) {
            throw new IllegalArgumentException("Not a Trivy schema 2 report");
        }
        List<Finding> findings = new ArrayList<>();
        // No Results means nothing to report, as Trivy writes for an empty SBOM.
        for (JsonNode result : array(root.path("Results"), "Results")) {
            for (JsonNode vulnerability : array(result.path("Vulnerabilities"), "Vulnerabilities")) {
                if ("not_affected".equals(vulnerability.path("Status").asString(""))) {
                    continue;
                }
                findings.add(finding(vulnerability));
            }
        }
        return findings;
    }

    private static Finding finding(JsonNode vulnerability) {
        String id = vulnerability.required("VulnerabilityID").asString();
        Set<String> aliases = new LinkedHashSet<>();
        for (JsonNode vendorId : array(vulnerability.path("VendorIDs"), "VendorIDs")) {
            if (vendorId.isString() && !vendorId.asString().equals(id)) {
                aliases.add(vendorId.asString());
            }
        }
        return new Finding(
                Finding.Kind.VULNERABILITY,
                id,
                List.copyOf(aliases),
                purl(vulnerability),
                Severities.named(vulnerability.path("Severity").asString(null)),
                ToolText.clean(vulnerability.path("FixedVersion").asString(null), 255),
                ToolText.clean(vulnerability.path("Title").asString(null), 512));
    }

    private static @Nullable String purl(JsonNode vulnerability) {
        JsonNode purl = vulnerability.path("PkgIdentifier").path("PURL");
        return purl.isString() && purl.asString().startsWith("pkg:") ? purl.asString() : null;
    }

    /** A list, or none when the field is absent; anything else is a report Kiln can't read. */
    static Iterable<JsonNode> array(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " is not a list");
        }
        return node;
    }
}
