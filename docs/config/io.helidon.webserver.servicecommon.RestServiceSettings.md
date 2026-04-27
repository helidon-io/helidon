# io.helidon.webserver.servicecommon.RestServiceSettings

## Description

Common settings across REST services

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
<code>routing</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the routing name to use for setting up the service's endpoint</td>
</tr>
<tr>
<td>
<code>web-context</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the web context to use for the service's endpoint</td>
</tr>
<tr>
<td>
<a id="cors"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cors</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, CrossOriginConfig&gt;">Map&lt;String, CrossOriginConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the cross-origin config builder for use in establishing CORS support for the service endpoints</td>
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
<td>Is this service enabled or not</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
