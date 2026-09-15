# io.<wbr>helidon.<wbr>webclient.<wbr>http3.<wbr>Http3Client<wbr>Protocol<wbr>Config

## Description

Configuration of the HTTP/3 client protocol

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
<code>stream-<wbr>open-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Maximum duration to wait for peer QUIC stream credit when opening local HTTP/3 request and critical streams</td>
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
<code>initial-<wbr>response-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Maximum duration to wait after sending the first client QUIC Initial packet until receiving the first peer Initial packet</td>
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
<code>-1</code>
</td>
<td>Maximum field section size the client advertises to servers in HTTP/3 SETTINGS</td>
</tr>
<tr>
<td>
<a id="quic"></a>
<a href="io.helidon.quic.QuicConfig.md">
<code>quic</code>
</a>
</td>
<td>
<code>Quic<wbr>Config</code>
</td>
<td>
</td>
<td>QUIC protocol and transport configuration used for HTTP/3 connections created from this protocol configuration</td>
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
<td>Configure the maximum allowed headers size</td>
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
<code>prior-<wbr>knowledge</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Prior knowledge of HTTP/3 capabilities of the server</td>
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
<td>Maximum QPACK dynamic table capacity announced by the client</td>
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
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>h3</code>
</td>
<td>Name of this HTTP/3 protocol configuration</td>
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
<td>Maximum number of QPACK blocked streams announced by the client</td>
</tr>
<tr>
<td>
<code>handshake-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Handshake timeout for a complete new HTTP/3 connection attempt</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
