# io.helidon.webclient.http2.Http2ClientProtocolConfig

## Description

<code>N/A</code>

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
<code>flow-control-block-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT15S</code>
</td>
<td>Timeout for blocking while waiting for window update when window is depleted</td>
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
<code>prior-knowledge</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Prior knowledge of HTTP/2 capabilities of the server</td>
</tr>
<tr>
<td>
<code>max-frame-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">16384</code>
</td>
<td>Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td>
<code>ping</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Check healthiness of cached connections with HTTP/2.0 ping frame</td>
</tr>
<tr>
<td>
<code>ping-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.5S</code>
</td>
<td>Timeout for ping probe used for checking healthiness of cached connections</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">h2</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>max-header-list-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td>
<code>initial-window-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">65535</code>
</td>
<td>Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
