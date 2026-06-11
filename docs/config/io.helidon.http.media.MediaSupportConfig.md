# io.helidon.http.media.MediaSupportConfig

## Description

A set of configurable options expected to be used by each media support

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>accepted-<wbr>media-<wbr>types</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Types accepted by this media support</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the support</td>
</tr>
<tr>
<td>
<code>content-<wbr>type</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Content type to use if not configured (in response headers for server, and in request headers for client)</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
