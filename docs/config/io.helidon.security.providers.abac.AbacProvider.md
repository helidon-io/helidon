# io.helidon.security.providers.abac.AbacProvider

## Description

Attribute Based Access Control provider

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
<code>fail-if-none-validated</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to fail if NONE of the attributes is validated</td>
</tr>
<tr>
<td>
<code>fail-on-unvalidated</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
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
