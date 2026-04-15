# io.helidon.http.media.MediaSupportConfig

## Description

A set of configurable options expected to be used by each media support

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>accepted-media-types</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td>Types accepted by this media support</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td>Name of the support</td>
</tr>
<tr>
<td><code>content-type</code></td>
<td><code>CustomMethods</code></td>
<td>Content type to use if not configured (in response headers for server, and in request headers for client)</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
