# com.acme.AcmeSqlDataSourceConfig

## Description

ACME SQL data source configuration

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
<code>max-pool-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20</code>
</td>
<td>Maximum pool size</td>
</tr>
<tr>
<td>
<code>url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JDBC URL</td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql`](io.helidon.data.SourcesConfig.md#sql)

---

See the [manifest](manifest.md) for all available types.
