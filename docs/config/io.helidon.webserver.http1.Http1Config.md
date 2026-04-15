# io.helidon.webserver.http1.Http1Config

## Description

HTTP/1.1 server configuration

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
<td><code>continue-immediately</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>When true WebServer answers to expect continue with 100 continue immediately, not waiting for user to actually request the data</td>
</tr>
<tr>
<td><code>validate-response-headers</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td><code>validate-prologue</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to false, any query and fragment is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td><code>validate-path</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>If set to false, any path is accepted (even containing illegal characters)</td>
</tr>
<tr>
<td><code>max-headers-size</code></td>
<td><code>Integer</code></td>
<td><code>16384</code></td>
<td>Maximal size of received headers in bytes</td>
</tr>
<tr>
<td><code>max-buffered-entity-size</code></td>
<td><code>Size</code></td>
<td><code>64 KB</code></td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling &lt;code&gt;io.helidon.http.media.ReadableEntity#buffer&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="requested-uri-discovery"></a><a href="io.helidon.http.RequestedUriDiscoveryContext.md"><code>requested-uri-discovery</code></a></td>
<td><code>RequestedUriDiscoveryContext</code></td>
<td></td>
<td>Requested URI discovery settings</td>
</tr>
<tr>
<td><code>validate-request-headers</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td><code>max-prologue-length</code></td>
<td><code>Integer</code></td>
<td><code>4096</code></td>
<td>Maximal size of received HTTP prologue (GET /path HTTP/1.1)</td>
</tr>
<tr>
<td><code>send-log</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Logging of sent packets</td>
</tr>
<tr>
<td><code>recv-log</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Logging of received packets</td>
</tr>
<tr>
<td><code>send-keep-alive-header</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to send the default &lt;code&gt;Connection: keep-alive&lt;/code&gt; response header for persistent HTTP/1.1 connections</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>validate-request-host-header</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Request host header validation</td>
</tr>
</tbody>
</table>

## Usages

- [`server.protocols.http_1_1`](io.helidon.webserver.spi.ProtocolConfig.md#http_1_1)
- [`server.sockets.protocols.http_1_1`](io.helidon.webserver.spi.ProtocolConfig.md#http_1_1)

---

See the [manifest](manifest.md) for all available types.
