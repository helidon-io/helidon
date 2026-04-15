# io.helidon.telemetry.otelconfig.OtlpHttpExporterConfig

## Description

Settings common to HTTP-based OTLP exporters

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>connect-timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Connection timeout</td>
</tr>
<tr>
<td><code>headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Headers added to each export message</td>
</tr>
<tr>
<td><code>endpoint</code></td>
<td><code>URI</code></td>
<td></td>
<td>Endpoint of the collector to which the exporter should transmit</td>
</tr>
<tr>
<td><a id="memory-mode"></a><a href="io.opentelemetry.sdk.common.export.MemoryMode.md"><code>memory-mode</code></a></td>
<td><code>MemoryMode</code></td>
<td></td>
<td>Memory mode</td>
</tr>
<tr>
<td><code>protocol</code></td>
<td><code>CustomMethods</code></td>
<td><code>DEFAULT</code></td>
<td>Exporter protocol type</td>
</tr>
<tr>
<td><a id="internal-telemetry-version"></a><a href="io.opentelemetry.sdk.common.InternalTelemetryVersion.md"><code>internal-telemetry-version</code></a></td>
<td><code>InternalTelemetryVersion</code></td>
<td></td>
<td>Self-monitoring telemetry OpenTelemetry should collect</td>
</tr>
<tr>
<td><a id="certificate"></a><a href="io.helidon.common.configurable.Resource.md"><code>certificate</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>Trusted certificates</td>
</tr>
<tr>
<td><a id="client-key"></a><a href="io.helidon.common.configurable.Resource.md"><code>client.key</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>TLS client key</td>
</tr>
<tr>
<td><a id="client-certificate"></a><a href="io.helidon.common.configurable.Resource.md"><code>client.certificate</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>TLS certificate</td>
</tr>
<tr>
<td><a id="compression"></a><a href="io.helidon.telemetry.otelconfig.CompressionType.md"><code>compression</code></a></td>
<td><code>CompressionType</code></td>
<td></td>
<td>Compression the exporter uses</td>
</tr>
<tr>
<td><code>retry-policy</code></td>
<td><code>CustomMethods</code></td>
<td></td>
<td>Retry policy</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Exporter timeout</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
