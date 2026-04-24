# io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfig

## Description

JDBC Data source configuration

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
<code>schema</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the default schema name to be set on connections</td>
</tr>
<tr>
<td>
<code>auto-commit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Set the default auto-commit behavior of create connections</td>
</tr>
<tr>
<td>
<code>catalog</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the default catalog name to be set on connections</td>
</tr>
<tr>
<td>
<code>read-only</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether the connection should be read only</td>
</tr>
<tr>
<td>
<a id="transaction-isolation"></a>
<a href="io.helidon.data.sql.datasource.TransactionIsolation.md">
<code>transaction-isolation</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TransactionIsolation">TransactionIsolation</code>
</td>
<td>Set the default transaction isolation level</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td>Add properties (name/value pair) that will be used to configure the DataSource/Driver</td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql.provider.jdbc`](io.helidon.data.sql.datasource.ProviderConfig.md#jdbc)

---

See the [manifest](manifest.md) for all available types.
