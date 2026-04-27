# io.helidon.telemetry.otelconfig.MetricExporterType

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>OTLP</code></td>
<td>OpenTelemetry Protocol <code>io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter</code> and <code>io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter</code></td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console (<code>io.opentelemetry.exporter.logging.LoggingMetricExporter</code></td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>JSON logging to console <code>io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter</code></td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
