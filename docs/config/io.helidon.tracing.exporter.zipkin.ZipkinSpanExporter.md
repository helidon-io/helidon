# io.<wbr>helidon.<wbr>tracing.<wbr>exporter.<wbr>zipkin.<wbr>Zipkin<wbr>Span<wbr>Exporter

## Description

Configuration for the transitional Zipkin HTTP exporter

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
<code>endpoint</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>http:<wbr>//localhost:<wbr>9411/<wbr>api/<wbr>v2/spans</code>
</td>
<td>Collector HTTP or HTTPS endpoint</td>
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
<code>LEGACY</code>
</td>
<td>Exporter self-observability metric version</td>
</tr>
<tr>
<td>
<code>compression</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>gzip</code>
</td>
<td>Payload compression, either <code>gzip</code> or <code>none</code></td>
</tr>
<tr>
<td>
<a id="encoder"></a>
<a href="zipkin2.reporter.SpanBytesEncoder.md">
<code>encoder</code>
</a>
</td>
<td>
<code>Span<wbr>Bytes<wbr>Encoder</code>
</td>
<td>
<code>JSON_<wbr>V2</code>
</td>
<td>Span encoder</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Socket read timeout</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
