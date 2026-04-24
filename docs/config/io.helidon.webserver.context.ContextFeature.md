# io.helidon.webserver.context.ContextFeature

## Description

Configuration of context feature

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<a id="records"></a>
<a href="io.helidon.common.context.http.ContextRecordConfig.md">
<code>records</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ContextRecordConfig&gt;">List&lt;ContextRecordConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of propagation records</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1100.0</code>
</td>
<td>Weight of the context feature</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of sockets to register this feature on</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.context`](io.helidon.webserver.spi.ServerFeature.md#context)

---

See the [manifest](manifest.md) for all available types.
