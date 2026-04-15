# io.helidon.telemetry.otelconfig.ZipkinExporterConfig

## Description

&lt;code&gt;N/A&lt;/code&gt;

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>endpoint</code></td>
<td><code>URI</code></td>
<td>Collector endpoint to which this exporter should transmit</td>
</tr>
<tr>
<td><a id="compression"></a><a href="io.helidon.telemetry.otelconfig.CompressionType.md"><code>compression</code></a></td>
<td><code>CompressionType</code></td>
<td>Compression type</td>
</tr>
<tr>
<td><a id="encoder"></a><a href="zipkin2.codec.SpanBytesEncoder.md"><code>encoder</code></a></td>
<td><code>SpanBytesEncoder</code></td>
<td>Encoder type</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td>Exporter timeout</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
