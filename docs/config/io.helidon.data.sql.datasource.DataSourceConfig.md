# io.helidon.data.sql.datasource.DataSourceConfig

## Description

&lt;code&gt;javax.sql.DataSource&lt;/code&gt; configuration

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
<td><a id="provider"></a><a href="io.helidon.data.sql.datasource.ProviderConfig.md"><code>provider</code></a></td>
<td><code>ProviderConfig</code></td>
<td></td>
<td>Configuration of the used provider, such as UCP</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td><code>@default</code></td>
<td>&lt;code&gt;javax.sql.DataSource&lt;/code&gt; name</td>
</tr>
<tr>
<td><code>provider-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;provider&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`data.sources.sql`](io.helidon.data.SourcesConfig.md#sql)

---

See the [manifest](manifest.md) for all available types.
