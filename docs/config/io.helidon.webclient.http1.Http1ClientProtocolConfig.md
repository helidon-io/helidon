# io.<wbr>helidon.<wbr>webclient.<wbr>http1.<wbr>Http1Client<wbr>Protocol<wbr>Config

## Description

Configuration of an HTTP/1.1 client

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
<td>Sets whether the response header format is validated or not</td>
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
<code>max-<wbr>header-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>16384</code>
</td>
<td>Configure the maximum allowed header size of the response</td>
</tr>
<tr>
<td>
<code>validate-<wbr>request-<wbr>headers</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Sets whether the request header format is validated or not</td>
</tr>
<tr>
<td>
<code>max-<wbr>status-<wbr>line-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>256</code>
</td>
<td>Configure the maximum allowed length of the status line from the response</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>http_<wbr>1_1</code>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<code>default-<wbr>keep-<wbr>alive</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to use keep alive by default</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
