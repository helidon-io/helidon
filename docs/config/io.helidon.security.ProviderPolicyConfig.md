# io.helidon.security.ProviderPolicyConfig

## Description

Configuration for security.provider-policy

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
<code>class-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td class="cm-default-cell">
</td>
<td>Provider selection policy class name, only used when type is set to CLASS</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.security.ProviderSelectionPolicyType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ProviderSelectionPolicyType">ProviderSelectionPolicyType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">FIRST</code>
</td>
<td>Type of the policy</td>
</tr>
</tbody>
</table>



## Usages

- [`security.provider-policy`](io.helidon.security.Security.md#provider-policy)

---

See the [manifest](manifest.md) for all available types.
