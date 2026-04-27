# io.helidon.telemetry.otelconfig.SpanExporterType

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
<td>OpenTelemetry Protocol <code>io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter</code> and <code>io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter</code></td>
</tr>
<tr>
<td><code>ZIPKIN</code></td>
<td>Zipkin <code>io.opentelemetry.exporter.zipkin.ZipkinSpanExporter</code></td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console (<code>io.opentelemetry.exporter.logging.LoggingSpanExporter</code></td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>JSON logging to console <code>io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter</code></td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
