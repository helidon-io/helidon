# io.helidon.webserver.http2.Http2Config

## Description

HTTP/2 server configuration

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
<td><code>max-buffered-entity-size</code></td>
<td><code>Size</code></td>
<td><code>64 KB</code></td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling &lt;code&gt;io.helidon.http.media.ReadableEntity#buffer&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-rapid-resets</code></td>
<td><code>Integer</code></td>
<td><code>50</code></td>
<td>Maximum number of rapid resets(stream RST sent by client before any data have been sent by server)</td>
</tr>
<tr>
<td><code>max-concurrent-streams</code></td>
<td><code>Long</code></td>
<td><code>8192</code></td>
<td>Maximum number of concurrent streams that the server will allow</td>
</tr>
<tr>
<td><code>rapid-reset-check-period</code></td>
<td><code>Duration</code></td>
<td><code>PT10S</code></td>
<td>Period for counting rapid resets(stream RST sent by client before any data have been sent by server)</td>
</tr>
<tr>
<td><code>max-frame-size</code></td>
<td><code>Integer</code></td>
<td><code>16384</code></td>
<td>The size of the largest frame payload that the sender is willing to receive in bytes</td>
</tr>
<tr>
<td><a id="requested-uri-discovery"></a><a href="io.helidon.http.RequestedUriDiscoveryContext.md"><code>requested-uri-discovery</code></a></td>
<td><code>RequestedUriDiscoveryContext</code></td>
<td></td>
<td>Requested URI discovery settings</td>
</tr>
<tr>
<td><code>flow-control-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT15S</code></td>
<td>Outbound flow control blocking timeout configured as &lt;code&gt;java.time.Duration&lt;/code&gt; or text in ISO-8601 format</td>
</tr>
<tr>
<td><code>send-error-details</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to send error message over HTTP to client</td>
</tr>
<tr>
<td><code>max-header-list-size</code></td>
<td><code>Long</code></td>
<td><code>8192</code></td>
<td>The maximum field section size that the sender is prepared to accept in bytes</td>
</tr>
<tr>
<td><code>initial-window-size</code></td>
<td><code>Integer</code></td>
<td><code>1048576</code></td>
<td>This setting indicates the sender&#x27;s maximum window size in bytes for stream-level flow control</td>
</tr>
<tr>
<td><code>validate-path</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to false, any path is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td><code>max-empty-frames</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Maximum number of consecutive empty frames allowed on connection</td>
</tr>
</tbody>
</table>


## Usages

- [`server.protocols.http_2`](io.helidon.webserver.spi.ProtocolConfig.md#http_2)
- [`server.sockets.protocols.http_2`](io.helidon.webserver.spi.ProtocolConfig.md#http_2)

---

See the [manifest](manifest.md) for all available types.
