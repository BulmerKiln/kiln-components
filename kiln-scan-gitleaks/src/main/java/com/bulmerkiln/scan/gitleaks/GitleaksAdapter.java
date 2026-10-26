/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.scan.gitleaks;

import com.bulmerkiln.scan.Finding;
import com.bulmerkiln.scan.ScanJob;
import com.bulmerkiln.scan.ScannerAdapter;
import com.bulmerkiln.scan.ScannerId;
import com.bulmerkiln.scan.Severity;
import com.bulmerkiln.scan.ToolOutput;
import com.bulmerkiln.scan.ToolRun;
import com.bulmerkiln.scan.ToolText;
import com.bulmerkiln.scan.ToolVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Gitleaks, on the unpacked content inside the sandbox. It runs with
 * {@code --redact}, and on top of that this adapter reads only the rule, never {@code Match},
 * {@code Secret} or the surrounding text. Exits 1 when it finds leaks.
 */
public final class GitleaksAdapter implements ScannerAdapter {

    public static final ScannerId ID = new ScannerId("gitleaks");

    private static final ToolVersion MINIMUM = new ToolVersion(8, 30, 0);

    @Override
    public ScannerId scanner() {
        return ID;
    }

    @Override
    public boolean supportsVersion(String toolVersion) {
        return ToolVersion.supported(toolVersion, MINIMUM, 0);
    }

    @Override
    public boolean completed(int exitCode) {
        return exitCode == 0 || exitCode == 1;
    }

    @Override
    public List<String> command(ScanJob job, Path input) {
        return List.of(
                "/usr/local/bin/gitleaks",
                "dir",
                input.toString(),
                "--report-format",
                "json",
                "--report-path",
                "-",
                "--redact",
                "--no-banner",
                "--log-level",
                "error");
    }

    @Override
    public List<Finding> parse(ScanJob job, ToolRun run) {
        JsonNode root = ToolOutput.json(run.output());
        if (!root.isArray()) {
            throw new IllegalArgumentException("Not a Gitleaks report");
        }
        List<Finding> findings = new ArrayList<>();
        for (JsonNode leak : root) {
            // A leaked credential is always serious; Gitleaks has no severities of its own.
            findings.add(new Finding(
                    Finding.Kind.SECRET,
                    leak.required("RuleID").asString(),
                    List.of(),
                    null,
                    Severity.HIGH,
                    null,
                    ToolText.clean(leak.path("Description").asString(null), 512)));
        }
        return findings;
    }
}
