# io.helidon.telemetry.otelconfig.OtlpHttpExporterConfig

## Description

Settings common to HTTP-based OTLP exporters

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>connect-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Connection timeout</td>
</tr>
<tr>
<td>
<code>headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Headers added to each export message</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>Endpoint of the collector to which the exporter should transmit</td>
</tr>
<tr>
<td>
<a id="memory-mode"></a>
<a href="io.opentelemetry.sdk.common.export.MemoryMode.md">
<code>memory-mode</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">MemoryMode</code>
</td>
<td class="cm-default-cell">
</td>
<td>Memory mode</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">DEFAULT</code>
</td>
<td>Exporter protocol type</td>
</tr>
<tr>
<td>
<a id="internal-telemetry-version"></a>
<a href="io.opentelemetry.sdk.common.InternalTelemetryVersion.md">
<code>internal-telemetry-version</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="InternalTelemetryVersion">InternalTelemetryVersion</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
</td>
<td>Trusted certificates</td>
</tr>
<tr>
<td>
<a id="client-key"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client.key</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
</td>
<td>TLS client key</td>
</tr>
<tr>
<td>
<a id="client-certificate"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client.certificate</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CompressionType">CompressionType</code>
</td>
<td class="cm-default-cell">
</td>
<td>Compression the exporter uses</td>
</tr>
<tr>
<td>
<code>retry-policy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>Retry policy</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Exporter timeout</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
