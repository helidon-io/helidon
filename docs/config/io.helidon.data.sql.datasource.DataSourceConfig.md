# io.helidon.data.sql.datasource.DataSourceConfig

## Description

<code>javax.<wbr>sql.<wbr>Data<wbr>Source</code> configuration

## Configuration options


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
<td>
<code>Provider<wbr>Config</code>
</td>
<td>
</td>
<td>Configuration of the used provider, such as UCP</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>@default</code>
</td>
<td><code>javax.<wbr>sql.<wbr>Data<wbr>Source</code> name</td>
</tr>
<tr>
<td>
<code>provider-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>provider</code></td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql`](io.helidon.data.SourcesConfig.md#sql)

---

See the [manifest](manifest.md) for all available types.
