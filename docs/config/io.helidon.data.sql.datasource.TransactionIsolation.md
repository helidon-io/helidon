# io.helidon.data.sql.datasource.TransactionIsolation

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>TRANSACTION_READ_UNCOMMITTED</code></td>
<td><code>N/A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_READ_COMMITTED</code></td>
<td><code>N/A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_REPEATABLE_READ</code></td>
<td><code>N/A</code></td>
</tr>
<tr>
<td><code>TRANSACTION_SERIALIZABLE</code></td>
<td><code>N/A</code></td>
</tr>
</tbody>
</table>

## Usages

- [`data.sources.sql.provider.hikari.transaction-isolation`](io.helidon.data.sql.datasource.hikari.HikariDataSourceConfig.md#transaction-isolation)
- [`data.sources.sql.provider.jdbc.transaction-isolation`](io.helidon.data.sql.datasource.jdbc.JdbcDataSourceConfig.md#transaction-isolation)

---

See the [manifest](manifest.md) for all available types.
