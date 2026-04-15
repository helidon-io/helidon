# io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig

## Description

Configuration settings for OpenTelemetry logging

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
<td><code>trace-based</code></td>
<td><code>Boolean</code></td>
<td>Whether to include &lt;em&gt;only&lt;/em&gt; log records from traces which are sampled</td>
</tr>
<tr>
<td><code>attributes</code></td>
<td><code>CustomMethods</code></td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td><a id="minimum-severity"></a><a href="io.opentelemetry.api.logs.Severity.md"><code>minimum-severity</code></a></td>
<td><code>Severity</code></td>
<td>Minimum severity level of log records to process</td>
</tr>
<tr>
<td><code>processors</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td>Settings for logging processors</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td>Whether the OpenTelemetry logger should be enabled</td>
</tr>
<tr>
<td><code>log-limits</code></td>
<td><code>CustomMethods</code></td>
<td>Log limits to apply to log transmission</td>
</tr>
<tr>
<td><code>exporters</code></td>
<td><code>Map&lt;String, CustomMethods&gt;</code></td>
<td>Log record exporters</td>
</tr>
</tbody>
</table>


## Usages

- [`telemetry.signals.logging`](io.helidon.telemetry.SignalsConfig.md#logging)

---

See the [manifest](manifest.md) for all available types.
