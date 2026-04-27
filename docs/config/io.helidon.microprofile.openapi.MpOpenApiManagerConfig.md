# io.helidon.microprofile.openapi.MpOpenApiManagerConfig

## Description

<code>MpOpenApiManager</code> prototype

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>mp.openapi.extensions.helidon.use-jaxrs-semantics</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>If <code>true</code> and the <code>jakarta.ws.rs.core.Application</code> class returns a non-empty set, endpoints defined by other resources are not included in the OpenAPI document</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
