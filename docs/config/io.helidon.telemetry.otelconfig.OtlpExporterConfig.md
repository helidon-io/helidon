# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Otlp<wbr>Exporter<wbr>Config

## Description

Settings for OpenTelemetry OTLP exporters

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>connect-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Connection timeout</td>
</tr>
<tr>
<td>
<code>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Headers added to each export message</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>Endpoint of the collector to which the exporter should transmit</td>
</tr>
<tr>
<td>
<a id="memory-mode"></a>
<a href="io.opentelemetry.sdk.common.export.MemoryMode.md">
<code>memory-<wbr>mode</code>
</a>
</td>
<td>
<code>Memory<wbr>Mode</code>
</td>
<td>
</td>
<td>Memory mode</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>DEFAULT</code>
</td>
<td>Exporter protocol type</td>
</tr>
<tr>
<td>
<a id="internal-telemetry-version"></a>
<a href="io.opentelemetry.sdk.common.InternalTelemetryVersion.md">
<code>internal-<wbr>telemetry-<wbr>version</code>
</a>
</td>
<td>
<code>Internal<wbr>Telemetry<wbr>Version</code>
</td>
<td>
</td>
<td>Self-monitoring telemetry OpenTelemetry should collect</td>
</tr>
<tr>
<td>
<a id="certificate"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>certificate</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>Trusted certificates</td>
</tr>
<tr>
<td>
<a id="client-key"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client.<wbr>key</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>TLS client key</td>
</tr>
<tr>
<td>
<a id="client-certificate"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client.<wbr>certificate</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>TLS certificate</td>
</tr>
<tr>
<td>
<a id="compression"></a>
<a href="io.helidon.telemetry.otelconfig.CompressionType.md">
<code>compression</code>
</a>
</td>
<td>
<code>Compression<wbr>Type</code>
</td>
<td>
</td>
<td>Compression the exporter uses</td>
</tr>
<tr>
<td>
<a id="retry-policy"></a>
<a href="io.helidon.telemetry.otelconfig.RetryPolicyConfig.md">
<code>retry-<wbr>policy</code>
</a>
</td>
<td>
<code>Retry<wbr>Policy<wbr>Config</code>
</td>
<td>
</td>
<td>Retry policy</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Exporter timeout</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>LogRecord<wbr>Exporter<wbr>Config](io.helidon.telemetry.otelconfig.LogRecordExporterConfig.md)
- [io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Metric<wbr>Exporter<wbr>Config](io.helidon.telemetry.otelconfig.MetricExporterConfig.md)
- [io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Otlp<wbr>Http<wbr>Exporter<wbr>Config](io.helidon.telemetry.otelconfig.OtlpHttpExporterConfig.md)

---

See the [manifest](manifest.md) for all available types.
