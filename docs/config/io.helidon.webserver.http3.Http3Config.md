# io.<wbr>helidon.<wbr>webserver.<wbr>http3.<wbr>Http3Config

## Description

HTTP/3 listener protocol configuration

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
<code>validate-<wbr>response-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Common HTTP response-header validation setting, ignored because HTTP/3 always validates response field names and values</td>
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
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling <code>io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Readable<wbr>Entity.<wbr>buffer(<wbr>)</code></td>
</tr>
<tr>
<td>
<code>qpack-<wbr>max-<wbr>table-<wbr>capacity</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>-1</code>
</td>
<td>Maximum QPACK dynamic table capacity announced by the server</td>
</tr>
<tr>
<td>
<code>response-<wbr>dispatch-<wbr>window-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>65536</code>
</td>
<td>Maximum number of response entity bytes that may be submitted on one HTTP/3 stream without waiting for those bytes to be handed to the QUIC packet path</td>
</tr>
<tr>
<td>
<a id="log"></a>
<a href="io.helidon.http.HttpLogConfig.md">
<code>log</code>
</a>
</td>
<td>
<code>Http<wbr>LogConfig</code>
</td>
<td>
</td>
<td>HTTP Log configuration</td>
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
<td>Common HTTP request-header validation setting, ignored because HTTP/3 always validates request field names and values</td>
</tr>
<tr>
<td>
<code>qpack-<wbr>blocked-<wbr>streams</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>-1</code>
</td>
<td>Maximum number of QPACK blocked streams announced by the server</td>
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
<td>Whether exception-message text may be sent in HTTP/3 connection-close frames after control and formatting characters are replaced with spaces and the payload is truncated to 256 UTF-8 bytes; sensitive content is not redacted</td>
</tr>
<tr>
<td>
<code>max-<wbr>field-<wbr>section-<wbr>size</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>8192</code>
</td>
<td>Maximum field section size the server advertises to clients in HTTP/3 SETTINGS</td>
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
<td>Whether to perform additional URI path validation beyond mandatory HTTP/3 request-target checks</td>
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
<td>Configure the maximum allowed headers size, which must be greater than <code>0</code></td>
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
<td>Whether HTTP/3 is enabled on this listener</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_3"><code>server.<wbr>protocols.<wbr>http_<wbr>3</code></a>
- <a href="io.helidon.webserver.spi.ProtocolConfig.md#http_3"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>3</code></a>

---

See the [manifest](manifest.md) for all available types.
