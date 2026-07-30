# io.<wbr>helidon.<wbr>webclient.<wbr>http2.<wbr>Http2Client<wbr>Protocol<wbr>Config

## Description

Configuration of an HTTP/2 client

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
<code>flow-<wbr>control-<wbr>block-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT15S</code>
</td>
<td>Timeout for blocking while waiting for window update when window is depleted</td>
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
<td>Prior knowledge of HTTP/2 capabilities of the server</td>
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
<td>Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td>
<code>ping</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Check healthiness of cached connections with HTTP/2.0 ping frame</td>
</tr>
<tr>
<td>
<code>ping-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT0.<wbr>5S</code>
</td>
<td>Timeout for ping probe used for checking healthiness of cached connections</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>h2</code>
</td>
<td>Name of this protocol configuration</td>
</tr>
<tr>
<td>
<code>max-<wbr>header-<wbr>list-<wbr>size</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>&#8288;-&#8288;1</code>
</td>
<td>Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td>
<code>initial-<wbr>window-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>65535</code>
</td>
<td>Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
