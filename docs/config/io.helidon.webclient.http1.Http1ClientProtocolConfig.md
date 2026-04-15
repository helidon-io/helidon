# io.helidon.webclient.http1.Http1ClientProtocolConfig

## Description

Configuration of an HTTP/1.1 client

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
<td><code>validate-response-headers</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Sets whether the response header format is validated or not</td>
</tr>
<tr>
<td><code>max-buffered-entity-size</code></td>
<td><code>Size</code></td>
<td><code>64 KB</code></td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling &lt;code&gt;io.helidon.http.media.ReadableEntity#buffer&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-header-size</code></td>
<td><code>Integer</code></td>
<td><code>16384</code></td>
<td>Configure the maximum allowed header size of the response</td>
</tr>
<tr>
<td><code>validate-request-headers</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Sets whether the request header format is validated or not</td>
</tr>
<tr>
<td><code>max-status-line-length</code></td>
<td><code>Integer</code></td>
<td><code>256</code></td>
<td>Configure the maximum allowed length of the status line from the response</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td><code>http_1_1</code></td>
<td>&lt;code&gt;N/A&lt;/code&gt;</td>
</tr>
<tr>
<td><code>default-keep-alive</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to use keep alive by default</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
