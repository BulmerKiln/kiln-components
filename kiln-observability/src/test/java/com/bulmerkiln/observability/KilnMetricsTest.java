/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.format.ArtifactEvents;
import com.bulmerkiln.format.ArtifactRef;
import com.bulmerkiln.format.FormatAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KilnMetricsTest {

    private static final ArtifactFormat OCI = new ArtifactFormat("oci");
    private static final ArtifactFormat NPM = new ArtifactFormat("npm");
    private static final ArtifactFormat MAVEN = new ArtifactFormat("maven");
    private static final ArtifactFormat CARGO = new ArtifactFormat("cargo");

    private final Organization acme = new Organization(new OrganizationId(UUID.randomUUID()), "acme");

    @Test
    void exportsTheNamesObservabilityMdDefines() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        KilnMetrics metrics = new KilnMetrics(registry, false);
        metrics.push(OCI, "acme");
        metrics.pull(NPM, "@acme");
        metrics.bytesServed(OCI, "acme", 100);
        metrics.bytesReceived(MAVEN, "com.acme", 50);
        metrics.blockedRequest(CARGO, "acme");
        metrics.authFailure(4, AuthFailureReason.REVOKED);
        metrics.immutableTagDenial();
        metrics.leakedToken();

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("kiln_pushes_total{format=\"oci\",namespace=\"acme\"} 1.0")
                .contains("kiln_pulls_total{format=\"npm\",namespace=\"@acme\"} 1.0")
                .contains("kiln_bytes_served_total{format=\"oci\",namespace=\"acme\"} 100.0")
                .contains("kiln_bytes_received_total{format=\"maven\",namespace=\"com.acme\"} 50.0")
                .contains("kiln_blocked_requests_total{format=\"cargo\",namespace=\"acme\"} 1.0")
                .contains("kiln_auth_failures_total{reason=\"revoked\",rung=\"4\"} 1.0")
                .contains("kiln_immutable_tag_denials_total 1.0")
                .contains("kiln_leaked_tokens_total 1.0")
                .doesNotContain("organization=");
    }

    @Test
    void addsTheOrganizationLabelOnlyWhenEnabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrganizationContext.run(acme, () -> {
            new KilnMetrics(registry, false).push(OCI, "acme");
            new KilnMetrics(registry, true).push(OCI, "acme");
        });
        assertThat(registry.get("kiln.pushes")
                        .tag("organization", "acme")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.find("kiln.pushes").tagKeys("organization").counters())
                .hasSize(1);
    }

    @Test
    void omitsTheOrganizationLabelOutsideAnOrganization() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new KilnMetrics(registry, true).immutableTagDenial();
        assertThat(registry.get("kiln.immutable.tag.denials").counter().getId().getTag("organization"))
                .isNull();
    }

    /** Attacker-controlled names must not become label values: each would be a new time series. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "ACME",
                "acme\nkiln_fake_metric 1",
                "acme\"}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "../acme",
                "acme/../x",
                "acme{}",
                "\u0430cme"
            })
    void collapsesUnexpectedNamespacesToUnknown(String namespace) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new KilnMetrics(registry, false).pull(NPM, namespace);
        assertThat(registry.get("kiln.pulls")
                        .tag("namespace", KilnMetrics.UNKNOWN)
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void manyHostileNamesStayOneSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KilnMetrics metrics = new KilnMetrics(registry, false);
        for (int i = 0; i < 1000; i++) {
            metrics.pull(OCI, "X" + i);
        }
        assertThat(registry.find("kiln.pulls").counters()).hasSize(1);
    }

    @Test
    void boundsTheRungLabel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KilnMetrics metrics = new KilnMetrics(registry, false);
        metrics.authFailure(0, AuthFailureReason.INVALID);
        metrics.authFailure(5, AuthFailureReason.INVALID);
        metrics.authFailure(Integer.MIN_VALUE, AuthFailureReason.INVALID);
        assertThat(registry.get("kiln.auth.failures")
                        .tag("rung", "unknown")
                        .counter()
                        .count())
                .isEqualTo(3);
    }

    @Test
    void ignoresNonPositiveByteCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KilnMetrics metrics = new KilnMetrics(registry, false);
        assertThatCode(() -> {
                    metrics.bytesServed(OCI, "acme", 0);
                    metrics.bytesServed(OCI, "acme", -5);
                })
                .doesNotThrowAnyException();
        assertThat(registry.find("kiln.bytes.served").counter()).isNull();
    }

    @Test
    void autoConfiguresOnTheApplicationsRegistry() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(KilnMetrics.class));
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("kiln.observability.organization-label=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(KilnMetrics.class);
                    assertThat(context.getBean(ObservabilityProperties.class).organizationLabel())
                            .isTrue();
                });
    }

    @Test
    void countsEveryFormatsPushesAndPullsThroughTheEventsPort() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ArtifactEvents events = new ArtifactEvents(List.of(new MetricsEventListener(new KilnMetrics(registry, false))));
        events.pushed(new ArtifactRef(NPM, "@acme", "ui", "1.0.0"), 2048);
        events.pulled(new ArtifactRef(OCI, "acme", "app", "sha256:abc"), 4096);
        events.pulled(new ArtifactRef(OCI, "acme", "app", null), 0);

        assertThat(registry.get("kiln.pushes")
                        .tag("format", "npm")
                        .tag("namespace", "@acme")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("kiln.bytes.received")
                        .tag("format", "npm")
                        .counter()
                        .count())
                .isEqualTo(2048);
        assertThat(registry.get("kiln.pulls").tag("format", "oci").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("kiln.bytes.served")
                        .tag("format", "oci")
                        .counter()
                        .count())
                .isEqualTo(4096);
    }

    @Test
    void joinsTheFormatEventsWhenBothArePresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ObservabilityAutoConfiguration.class,
                        FormatMetricsAutoConfiguration.class,
                        FormatAutoConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(MetricsEventListener.class)
                            .hasSingleBean(ArtifactEvents.class);
                    context.getBean(ArtifactEvents.class).pushed(new ArtifactRef(OCI, "acme", "app", "1"), 1);
                    assertThat(context.getBean(MeterRegistry.class)
                                    .get("kiln.pushes")
                                    .counter()
                                    .count())
                            .isEqualTo(1);
                });
    }
}
