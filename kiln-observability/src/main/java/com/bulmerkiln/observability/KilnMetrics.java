/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.org.OrgContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.regex.Pattern;

/**
 * Records Kiln's metrics under the names in OBS-1. Recording never throws: a
 * metric must never fail the request it describes.
 *
 * <p>The {@code org} label is added only when {@code orgLabel} is set, which kiln-cloud does;
 * kiln-oss has one organization, so the label would only add cardinality.
 */
public final class KilnMetrics {

    /** Namespace names as Kiln stores them: OCI prefixes, npm scopes, Maven groupId prefixes. */
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9@][a-z0-9._-]{0,127}");

    static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;
    private final boolean orgLabel;

    public KilnMetrics(MeterRegistry registry, boolean orgLabel) {
        this.registry = registry;
        this.orgLabel = orgLabel;
    }

    public void push(ArtifactFormat format, String namespace) {
        counter("kiln.pushes", artifactTags(format, namespace)).increment();
    }

    public void pull(ArtifactFormat format, String namespace) {
        counter("kiln.pulls", artifactTags(format, namespace)).increment();
    }

    public void bytesServed(ArtifactFormat format, String namespace, long bytes) {
        if (bytes > 0) {
            counter("kiln.bytes.served", artifactTags(format, namespace)).increment((double) bytes);
        }
    }

    public void bytesReceived(ArtifactFormat format, String namespace, long bytes) {
        if (bytes > 0) {
            counter("kiln.bytes.received", artifactTags(format, namespace)).increment((double) bytes);
        }
    }

    public void blockedRequest(ArtifactFormat format, String namespace) {
        counter("kiln.blocked.requests", artifactTags(format, namespace)).increment();
    }

    /**
     * Counts a refused credential.
     *
     * @param rung the credential-ladder rung, 1 (trusted publishing) to 4 (tokens)
     */
    public void authFailure(int rung, AuthFailureReason reason) {
        String rungLabel = rung >= 1 && rung <= 4 ? Integer.toString(rung) : UNKNOWN;
        counter("kiln.auth.failures", withOrg(Tags.of("rung", rungLabel, "reason", reason.label())))
                .increment();
    }

    public void immutableTagDenial() {
        counter("kiln.immutable.tag.denials", withOrg(Tags.empty())).increment();
    }

    public void leakedToken() {
        counter("kiln.leaked.tokens", withOrg(Tags.empty())).increment();
    }

    private Tags artifactTags(ArtifactFormat format, String namespace) {
        String label = NAMESPACE.matcher(namespace).matches() ? namespace : UNKNOWN;
        return withOrg(Tags.of("format", format.id(), "namespace", label));
    }

    private Tags withOrg(Tags tags) {
        return orgLabel && OrgContext.isBound()
                ? tags.and("org", OrgContext.current().slug())
                : tags;
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
