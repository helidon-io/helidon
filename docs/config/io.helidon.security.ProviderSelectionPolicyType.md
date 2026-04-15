# io.helidon.security.ProviderSelectionPolicyType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>FIRST</code></td>
<td>Choose first provider from the list by default</td>
</tr>
<tr>
<td><code>COMPOSITE</code></td>
<td>Can compose multiple providers together to form a single logical provider</td>
</tr>
<tr>
<td><code>CLASS</code></td>
<td>Explicit class for a custom &lt;code&gt;ProviderSelectionPolicyType&lt;/code&gt;</td>
</tr>
</tbody>
</table>

## Usages

- [`security.provider-policy.type`](io.helidon.security.ProviderPolicyConfig.md#type)
- [`server.features.security.security.provider-policy.type`](io.helidon.server.features.security.security.ProviderPolicyConfig.md#type)

---

See the [manifest](manifest.md) for all available types.
