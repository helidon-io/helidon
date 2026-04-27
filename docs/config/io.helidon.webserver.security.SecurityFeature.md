# io.helidon.webserver.security.SecurityFeature

## Description

Configuration of security feature fow webserver

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
<a id="security"></a>
<a href="io.helidon.security.Security.md">
<code>security</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Security</code>
</td>
<td class="cm-default-cell">
</td>
<td>Security associated with this feature</td>
</tr>
<tr>
<td>
<a id="defaults"></a>
<a href="io.helidon.webserver.security.SecurityHandler.md">
<code>defaults</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SecurityHandler">SecurityHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>The default security handler</td>
</tr>
<tr>
<td>
<a id="paths"></a>
<a href="io.helidon.webserver.security.PathsConfig.md">
<code>paths</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;PathsConfig&gt;">List&lt;PathsConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for webserver paths</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">800.0</code>
</td>
<td>Weight of the security feature</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.security`](io.helidon.webserver.spi.ServerFeature.md#security)

---

See the [manifest](manifest.md) for all available types.
