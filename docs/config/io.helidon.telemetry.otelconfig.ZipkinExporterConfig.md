# io.helidon.telemetry.otelconfig.ZipkinExporterConfig

## Description

<code>N/A</code>

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td>Collector endpoint to which this exporter should transmit</td>
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
<td>Compression type</td>
</tr>
<tr>
<td>
<a id="encoder"></a>
<a href="zipkin2.codec.SpanBytesEncoder.md">
<code>encoder</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SpanBytesEncoder">SpanBytesEncoder</code>
</td>
<td>Encoder type</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Exporter timeout</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
