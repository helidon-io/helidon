# io.helidon.metrics.api.MetricsPublisher

## Description

This type is a provider contract.

## Implementations

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>



<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a href="io.helidon.metrics.providers.micrometer.OtlpPublisher.md">
<code>otlp</code>
</a>
</td>
<td>Settings for an OTLP publisher</td>
</tr>
<tr>
<td>
<a href="io.helidon.metrics.providers.micrometer.PrometheusPublisher.md">
<code>prometheus</code>
</a>
</td>
<td>Settings for a Micrometer Prometheus meter registry</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.publishers`](io.helidon.MetricsConfig.md#publishers)
- [`server.features.observe.observers.metrics.publishers`](io.helidon.webserver.observe.metrics.MetricsObserver.md#publishers)

---

See the [manifest](manifest.md) for all available types.
