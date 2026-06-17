# io.<wbr>helidon.<wbr>http.<wbr>Http<wbr>Config

## Description

Common configuration of HTTP protocol, regardless of its version

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
<td>Whether to validate response headers</td>
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
<td>Whether to validate request headers</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>webclient.<wbr>http1.<wbr>Http1Client<wbr>Protocol<wbr>Config](io.helidon.webclient.http1.Http1ClientProtocolConfig.md)
- [io.<wbr>helidon.<wbr>webclient.<wbr>http2.<wbr>Http2Client<wbr>Protocol<wbr>Config](io.helidon.webclient.http2.Http2ClientProtocolConfig.md)
- [io.<wbr>helidon.<wbr>webserver.<wbr>http1.<wbr>Http1Config](io.helidon.webserver.http1.Http1Config.md)
- [io.<wbr>helidon.<wbr>webserver.<wbr>http2.<wbr>Http2Config](io.helidon.webserver.http2.Http2Config.md)

---

See the [manifest](manifest.md) for all available types.
