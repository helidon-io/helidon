# io.helidon.telemetry.otelconfig.SpanExporterType

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
<td>OpenTelemetry Protocol &lt;code&gt;io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter&lt;/code&gt; and &lt;code&gt;io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>ZIPKIN</code></td>
<td>Zipkin &lt;code&gt;io.opentelemetry.exporter.zipkin.ZipkinSpanExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>CONSOLE</code></td>
<td>Console (&lt;code&gt;io.opentelemetry.exporter.logging.LoggingSpanExporter&lt;/code&gt;</td>
</tr>
<tr>
<td><code>LOGGING_OTLP</code></td>
<td>JSON logging to console &lt;code&gt;io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter&lt;/code&gt;</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
