# io.helidon.openapi.OpenApiFeature

## Description

<code>OpenApiFeature</code> prototype

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
<code>web-context</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">/openapi</code>
</td>
<td>Web context path for the OpenAPI endpoint</td>
</tr>
<tr>
<td>
<a id="manager"></a>
<a href="io.helidon.openapi.OpenApiManager.md">
<code>manager</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OpenApiManager">OpenApiManager</code>
</td>
<td class="cm-default-cell">
</td>
<td>OpenAPI manager</td>
</tr>
<tr>
<td>
<code>services-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>services</code></td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">openapi</code>
</td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td>
<code>static-file</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path of the static OpenAPI document file</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">90.0</code>
</td>
<td>Weight of the OpenAPI feature</td>
</tr>
<tr>
<td>
<a id="services"></a>
<a href="io.helidon.openapi.OpenApiService.md">
<code>services</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;OpenApiService&gt;">List&lt;OpenApiService&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>OpenAPI services</td>
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
<tr>
<td>
<code>manager-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>manager</code></td>
</tr>
<tr>
<td>
<code>permit-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Sets whether the feature should be enabled</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<a id="cors"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cors</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CrossOriginConfig">CrossOriginConfig</code>
</td>
<td>CORS config</td>
</tr>
</tbody>
</table>


## Usages

- [`openapi`](config_reference.md#openapi)
- [`server.features.openapi`](io.helidon.webserver.spi.ServerFeature.md#openapi)

---

See the [manifest](manifest.md) for all available types.
