# io.<wbr>helidon.<wbr>webserver.<wbr>http1.<wbr>Http1Config

## Description

HTTP/1.1 server configuration

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
<code>continue-<wbr>immediately</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>When true WebServer answers to expect continue with 100 continue immediately, not waiting for user to actually request the data</td>
</tr>
<tr>
<td>
<code>validate-<wbr>response-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td>
<code>validate-<wbr>prologue</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to false, any query and fragment is accepted (even containing illegal characters)</td>
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
<code>max-<wbr>headers-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>16384</code>
</td>
<td>Maximal size of received headers in bytes</td>
</tr>
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
<code>validate-<wbr>request-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to validate headers</td>
</tr>
<tr>
<td>
<code>max-<wbr>prologue-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>4096</code>
</td>
<td>Maximal size of received HTTP prologue (GET /path HTTP/1.1)</td>
</tr>
<tr>
<td>
<code>send-<wbr>log</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Logging of sent packets</td>
</tr>
<tr>
<td>
<code>recv-<wbr>log</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Logging of received packets</td>
</tr>
<tr>
<td>
<code>send-<wbr>keep-<wbr>alive-<wbr>header</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to send the default <code>Connection:<wbr> keep-<wbr>alive</code> response header for persistent HTTP/1.1 connections</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<code>validate-<wbr>request-<wbr>host-<wbr>header</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Request host header validation</td>
</tr>
</tbody>
</table>


## Usages

- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_1_1"><code>server.<wbr>protocols.<wbr>http_<wbr>1_1</code></a>
- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_1_1"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>1_1</code></a>

---

See the [manifest](manifest.md) for all available types.
