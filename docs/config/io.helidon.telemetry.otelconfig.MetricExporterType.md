# io.helidon.telemetry.otelconfig.MetricExporterType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>OTLP</code></td>
<td>OpenTelemetry Protocol &lt;code&gt;io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter&lt;/code&gt; and &lt;code&gt;io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console (&lt;code&gt;io.opentelemetry.exporter.logging.LoggingMetricExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>JSON logging to console &lt;code&gt;io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter&lt;/code&gt;</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
