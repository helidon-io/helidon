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
<td>OpenTelemetry Protocol &lt;code&gt;io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter&lt;/code&gt; and &lt;code&gt;io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder&lt;/code&gt;</td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console &lt;code&gt;io.opentelemetry.exporter.logging.SystemOutLogRecordExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>Writes logs to a Logger in OTLP JSON format &lt;code&gt;io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter&lt;/code&gt;</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
