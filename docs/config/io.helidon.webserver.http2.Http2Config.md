# io.<wbr>helidon.<wbr>webserver.<wbr>http2.<wbr>Http2Config

## Description

HTTP/2 server configuration

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
<code>max-<wbr>buffered-<wbr>entity-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>64 KB</code>
</td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling <code>io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Readable<wbr>Entity#<wbr>buffer</code></td>
</tr>
<tr>
<td>
<code>max-<wbr>rapid-<wbr>resets</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>50</code>
</td>
<td>Maximum number of rapid resets(stream RST sent by client before any data have been sent by server)</td>
</tr>
<tr>
<td>
<code>max-<wbr>concurrent-<wbr>streams</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>8192</code>
</td>
<td>Maximum number of concurrent streams that the server will allow</td>
</tr>
<tr>
<td>
<code>rapid-<wbr>reset-<wbr>check-<wbr>period</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Period for counting rapid resets(stream RST sent by client before any data have been sent by server)</td>
</tr>
<tr>
<td>
<code>max-<wbr>frame-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>16384</code>
</td>
<td>The size of the largest frame payload that the sender is willing to receive in bytes</td>
</tr>
<tr>
<td>
<a id="requested-uri-discovery"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.md">
<code>requested-<wbr>uri-<wbr>discovery</code>
</a>
</td>
<td>
<code>Requested<wbr>UriDiscovery<wbr>Context</code>
</td>
<td>
</td>
<td>Requested URI discovery settings</td>
</tr>
<tr>
<td>
<code>flow-<wbr>control-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT15S</code>
</td>
<td>Outbound flow control blocking timeout configured as <code>java.<wbr>time.<wbr>Duration</code> or text in ISO-8601 format</td>
</tr>
<tr>
<td>
<code>send-<wbr>error-<wbr>details</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to send error message over HTTP to client</td>
</tr>
<tr>
<td>
<code>max-<wbr>header-<wbr>list-<wbr>size</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>8192</code>
</td>
<td>The maximum field section size that the sender is prepared to accept in bytes</td>
</tr>
<tr>
<td>
<code>initial-<wbr>window-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1048576</code>
</td>
<td>This setting indicates the sender's maximum window size in bytes for stream-level flow control</td>
</tr>
<tr>
<td>
<code>validate-<wbr>path</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to false, any path is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td>
<code>max-<wbr>empty-<wbr>frames</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10</code>
</td>
<td>Maximum number of consecutive empty frames allowed on connection</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_2"><code>server.<wbr>protocols.<wbr>http_<wbr>2</code></a>
- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_2"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>2</code></a>

---

See the [manifest](manifest.md) for all available types.
