# io.helidon.webclient.http1.Http1ClientProtocolConfig

## Description

Configuration of an HTTP/1.1 client

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>validate-response-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Sets whether the response header format is validated or not</td>
</tr>
<tr>
<td>
<code>max-buffered-entity-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Size</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">64 KB</code>
</td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling <code>io.helidon.http.media.ReadableEntity#buffer</code></td>
</tr>
<tr>
<td>
<code>max-header-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">16384</code>
</td>
<td>Configure the maximum allowed header size of the response</td>
</tr>
<tr>
<td>
<code>validate-request-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Sets whether the request header format is validated or not</td>
</tr>
<tr>
<td>
<code>max-status-line-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">256</code>
</td>
<td>Configure the maximum allowed length of the status line from the response</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">http_1_1</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>default-keep-alive</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to use keep alive by default</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
