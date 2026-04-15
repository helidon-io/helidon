# io.helidon.webclient.http2.Http2ClientProtocolConfig

## Description

&lt;code&gt;N/A&lt;/code&gt;

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
<td><code>flow-control-block-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT15S</code></td>
<td>Timeout for blocking while waiting for window update when window is depleted</td>
</tr>
<tr>
<td><code>max-buffered-entity-size</code></td>
<td><code>Size</code></td>
<td><code>64 KB</code></td>
<td>Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling &lt;code&gt;io.helidon.http.media.ReadableEntity#buffer&lt;/code&gt;</td>
</tr>
<tr>
<td><code>prior-knowledge</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Prior knowledge of HTTP/2 capabilities of the server</td>
</tr>
<tr>
<td><code>max-frame-size</code></td>
<td><code>Integer</code></td>
<td><code>16384</code></td>
<td>Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td><code>ping</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Check healthiness of cached connections with HTTP/2.0 ping frame</td>
</tr>
<tr>
<td><code>ping-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT0.5S</code></td>
<td>Timeout for ping probe used for checking healthiness of cached connections</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td><code>h2</code></td>
<td>&lt;code&gt;N/A&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-header-list-size</code></td>
<td><code>Long</code></td>
<td><code>-1</code></td>
<td>Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections</td>
</tr>
<tr>
<td><code>initial-window-size</code></td>
<td><code>Integer</code></td>
<td><code>65535</code></td>
<td>Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
