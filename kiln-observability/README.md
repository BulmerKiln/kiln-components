# kiln-observability

Kiln's metrics, with labels bounded so no request can create new time series.

## At a glance

| | |
|---|---|
| Kind | Subsystem |
| Artifact | `com.bulmerkiln:kiln-observability` |
| Runs in | Kiln's server |
| Depends on | [`kiln-core`](../kiln-core), Micrometer; optionally [`kiln-format`](../kiln-format), to listen to its push and pull events |
| Used by | `kiln-oss-service`, `kiln-cloud-service` |
| Status | M4, Experimental: the artifact and auth metrics |

## Purpose

Operators watch Kiln through Prometheus and Alertmanager. This module records metrics under
stable names, and makes sure attackers can't inflate them: every label value comes from a fixed
vocabulary or a validated name, never from raw request text.

## Responsibilities

**Does**

- Record Kiln's metrics (`KilnMetrics`)
- Count every format's pushes, pulls and bytes, as a `kiln-format` event listener (`MetricsEventListener`)

**Does not**

- Write the audit log or events outbox; those are separate listeners
- Ship dashboards or alert rules yet

## Requirements

| ID | Requirement | Verified by |
|---|---|---|
| OBS-1 | Metrics must be exported under these Prometheus names: `kiln_pushes_total`, `kiln_pulls_total`, `kiln_bytes_served_total`, `kiln_bytes_received_total` and `kiln_blocked_requests_total` (labels `format`, `namespace`); `kiln_auth_failures_total` (labels `rung`, `reason`); `kiln_immutable_tag_denials_total`; `kiln_leaked_tokens_total` | `KilnMetricsTest` (against a real Prometheus scrape) |
| OBS-2 | Label values must come only from fixed vocabularies or validated names. A namespace that isn't a valid namespace name is recorded as `unknown`; a rung outside 1 to 4 as `unknown`; reasons are a fixed list | `KilnMetricsTest` |
| OBS-3 | The `org` label must be added only when `kiln.observability.org-label` is set and an organization is in context. kiln-oss has one organization, so the label would only add cardinality | `KilnMetricsTest` |
| OBS-4 | Recording must never throw and never fail the request it describes; non-positive byte counts are ignored | `KilnMetricsTest` |
| OBS-5 | When the format subsystem is present, every format's pushes and pulls must be counted through its events, without formats calling metrics directly | `KilnMetricsTest` |

## Contract *(subsystem only)*

| Type | Role |
|---|---|
| `KilnMetrics` | Records each metric, with bounded labels |
| `AuthFailureReason` | The fixed vocabulary for `reason` |
| `MetricsEventListener` | Implements `kiln-format`'s `ArtifactEventListener` |

**Rules enforced here**: bounded labels (OBS-2); metrics never fail requests (OBS-4).

## Adding an implementation *(subsystem only)*

New metrics are added to `KilnMetrics` with a fixed vocabulary for every label, and listed in
OBS-1. Another subsystem's events reach metrics the same way formats' do: through that
subsystem's listener port, not by calling `KilnMetrics` from feature code.

## Configuration

| Property | Default | Description |
|---|---|---|
| `kiln.observability.org-label` | `false` | Adds the `org` label to per-organization metrics; kiln-cloud sets it |

`KilnMetrics` is created only when the application has a Micrometer `MeterRegistry`.

## Usage

```java
metrics.authFailure(4, AuthFailureReason.REVOKED);
```

## Security

- **Threats**: an attacker sending many distinct names to create unbounded time series and exhaust
  Prometheus; label values used to inject fake metric lines
- **Untrusted input**: namespace names, which are validated before use as labels
- **Defaults**: no `org` label unless asked for
- **Protections**: OBS-2, OBS-4

## Testing

- **Contract suite**: none
- **Fuzz targets**: none
- **Other**: names are checked against a real Prometheus registry's scrape output

```bash
./gradlew :kiln-observability:test
```

No tests need Docker or external services.

## Standards and references

- **Standards**: [Prometheus naming conventions](https://prometheus.io/docs/practices/naming/); [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/specs/semconv/) for tracing, later
- **Dependencies**: Micrometer
- **Reference projects**: the kube-prometheus monitoring mixins

## Status

Implemented: OBS-1 to OBS-5.

Planned (M4): storage, GC, HTTP, scan and key-rotation metrics, the Prometheus and Alertmanager
adapters for the web app, the default alert rules, and tracing.
