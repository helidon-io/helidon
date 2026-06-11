# io.helidon.security.providers.abac.AbacProvider

## Description

Attribute Based Access Control provider

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
<code>fail-<wbr>if-none-<wbr>validated</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to fail if NONE of the attributes is validated</td>
</tr>
<tr>
<td>
<code>fail-<wbr>on-unvalidated</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to fail if any attribute is left unvalidated</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers.abac`](io.helidon.security.spi.SecurityProvider.md#abac)
- [`server.features.security.security.providers.abac`](io.helidon.security.spi.SecurityProvider.md#abac)

---

See the [manifest](manifest.md) for all available types.
