# io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig

## Description

OpenTelemetry metrics settings

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>readers</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td>Settings for metric readers</td>
</tr>
<tr>
<td><code>attributes</code></td>
<td><code>CustomMethods</code></td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td><code>views</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td>Metric view information, configurable using &lt;code&gt;io.helidon.telemetry.otelconfig.ViewRegistrationConfig&lt;/code&gt;</td>
</tr>
<tr>
<td><code>exporters</code></td>
<td><code>Map&lt;String, CustomMethods&gt;</code></td>
<td>Metric exporter configurations, configurable using &lt;code&gt;io.helidon.telemetry.otelconfig.MetricExporterConfig&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`telemetry.signals.metrics`](io.helidon.telemetry.SignalsConfig.md#metrics)

---

See the [manifest](manifest.md) for all available types.
