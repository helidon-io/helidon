# io.<wbr>helidon.<wbr>metrics.<wbr>publishers.<wbr>otlp.<wbr>Otlp<wbr>Publisher

## Description

Configuration of an OTLP HTTP/JSON publisher for Helidon metrics

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
<code>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Additional HTTP request headers, such as authentication headers</td>
</tr>
<tr>
<td>
<code>service-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>unknown_<wbr>service</code>
</td>
<td>Service name to use when the resource attributes do not contain <code>service.<wbr>name</code></td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td>
<code>URI</code>
</td>
<td>
<code>http:<wbr>//localhost:<wbr>4318/<wbr>v1/metrics</code>
</td>
<td>Complete HTTP or HTTPS endpoint for metrics, including its path</td>
</tr>
<tr>
<td>
<code>max-<wbr>request-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>64 Mi<wbr>B</code>
</td>
<td>Maximum size of an uncompressed JSON export request, measured in UTF-8 encoded bytes</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of this publisher instance</td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT60S</code>
</td>
<td>Delay between successive exports</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether this publisher is enabled</td>
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
<td>Time allowed for an export, including retries</td>
</tr>
<tr>
<td>
<code>resource-<wbr>attributes</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>String-valued resource attributes attached to each export</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
