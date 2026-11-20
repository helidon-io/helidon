# io.<wbr>helidon.<wbr>data.<wbr>sql.<wbr>datasource.<wbr>Data<wbr>Source<wbr>Health<wbr>Config

## Description

Configures health discovery without retaining or acquiring a data source

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
<code>query</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>SELECT 1</code>
</td>
<td>Specifies trusted SELECT text without bind markers</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Overrides the data source name in responses and individual check routes</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.health.HealthCheckType.md">
<code>type</code>
</a>
</td>
<td>
<code>Health<wbr>Check<wbr>Type</code>
</td>
<td>
<code>READINESS</code>
</td>
<td>Selects the health endpoint membership</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Enables health discovery for this data source definition</td>
</tr>
<tr>
<td>
<code>query-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT2S</code>
</td>
<td>Specifies a query timeout in positive whole seconds within the JDBC integer range</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.data.sql.datasource.DataSourceConfig.md#health"><code>data.<wbr>sources.<wbr>sql.<wbr>health</code></a>

---

See the [manifest](manifest.md) for all available types.
