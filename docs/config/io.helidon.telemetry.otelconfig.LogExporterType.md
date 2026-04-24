# io.helidon.telemetry.otelconfig.LogExporterType

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
<td>OpenTelemetry Protocol <code>io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter</code> and <code>io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder</code></td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console <code>io.opentelemetry.exporter.logging.SystemOutLogRecordExporter</code></td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>Writes logs to a Logger in OTLP JSON format <code>io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter</code></td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
