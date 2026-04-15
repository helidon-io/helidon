# io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig

## Description

OpenTelemetry tracer settings

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
<td><code>span-limits</code></td>
<td><code>CustomMethods</code></td>
<td>Tracing span limits</td>
</tr>
<tr>
<td><code>attributes</code></td>
<td><code>CustomMethods</code></td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td><code>processors</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td>Settings for span processors</td>
</tr>
<tr>
<td><code>exporters</code></td>
<td><code>Map&lt;String, CustomMethods&gt;</code></td>
<td>Span exporters</td>
</tr>
<tr>
<td><code>sampler</code></td>
<td><code>CustomMethods</code></td>
<td>Tracing sampler</td>
</tr>
</tbody>
</table>


## Usages

- [`telemetry.signals.tracing`](io.helidon.telemetry.SignalsConfig.md#tracing)

---

See the [manifest](manifest.md) for all available types.
