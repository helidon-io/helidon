# io.helidon.webserver.servicecommon.HelidonFeatureSupport

## Description

Common base implementation for <code>HttpService service</code> support classes which involve REST endpoints

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>web-context</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the root context for the REST API of the service</td>
</tr>
<tr>
<td>
<a id="cross-origin-config"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cross-origin-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CrossOriginConfig">CrossOriginConfig</code>
</td>
<td>Set the CORS config from the specified <code>CrossOriginConfig</code> object</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
