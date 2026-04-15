# io.helidon.webserver.context.ContextFeature

## Description

Configuration of context feature

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
<td><a id="records"></a><a href="io.helidon.common.context.http.ContextRecordConfig.md"><code>records</code></a></td>
<td><code>List&lt;ContextRecordConfig&gt;</code></td>
<td></td>
<td>List of propagation records</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>1100.0</code></td>
<td>Weight of the context feature</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of sockets to register this feature on</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.context`](io.helidon.webserver.spi.ServerFeature.md#context)

---

See the [manifest](manifest.md) for all available types.
