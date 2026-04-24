# io.helidon.data.sql.datasource.DataSourceConfig

## Description

<code>javax.sql.DataSource</code> configuration

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
<a id="provider"></a>
<a href="io.helidon.data.sql.datasource.ProviderConfig.md">
<code>provider</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ProviderConfig">ProviderConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration of the used provider, such as UCP</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">@default</code>
</td>
<td><code>javax.sql.DataSource</code> name</td>
</tr>
<tr>
<td>
<code>provider-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>provider</code></td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql`](io.helidon.data.SourcesConfig.md#sql)

---

See the [manifest](manifest.md) for all available types.
