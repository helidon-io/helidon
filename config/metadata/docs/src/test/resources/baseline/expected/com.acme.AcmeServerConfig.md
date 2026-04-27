# com.acme.AcmeServerConfig

## Description

ACME Server configuration.

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
<a id="features"></a>
<a href="com.acme.AcmeFeature.md">
<code>features</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;AcmeFeature&gt;">List&lt;AcmeFeature&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Dynamic features</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">8080</code>
</td>
<td>Listen port</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">localhost</code>
</td>
<td>Listen address</td>
</tr>
<tr>
<td>
<a id="sockets"></a>
<a href="com.acme.AcmeListenerConfig.md">
<code>sockets</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, AcmeListenerConfig&gt;">Map&lt;String, AcmeListenerConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sockets</td>
</tr>
</tbody>
</table>



## Usages

- [`server`](config_reference.md#server)

---

See the [manifest](manifest.md) for all available types.
