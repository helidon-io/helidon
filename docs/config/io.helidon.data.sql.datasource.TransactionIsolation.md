# io.helidon.data.sql.datasource.TransactionIsolation

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>TRANSACTION_<wbr>READ_<wbr>UNCOMMITTED</code></td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_<wbr>READ_<wbr>COMMITTED</code></td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_<wbr>REPEATABLE_<wbr>READ</code></td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_<wbr>SERIALIZABLE</code></td>
<td><code>N/<wbr>A</code></td>
</tr>
</tbody>
</table>

## Usages

- [`data.sources.sql.provider.hikari.transaction-isolation`](io.helidon.data.sql.datasource.hikari.HikariDataSourceConfig.md#transaction-isolation)
- [`data.sources.sql.provider.jdbc.transaction-isolation`](io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfig.md#transaction-isolation)

---

See the [manifest](manifest.md) for all available types.
