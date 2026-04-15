# io.helidon.server.features.security.security.ProviderPolicyConfig

## Description

Configuration for server.features.security.security.provider-policy

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>class-name</code></td>
<td><code>Class</code></td>
<td></td>
<td>Provider selection policy class name, only used when type is set to CLASS</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.security.ProviderSelectionPolicyType.md"><code>type</code></a></td>
<td><code>ProviderSelectionPolicyType</code></td>
<td><code>FIRST</code></td>
<td>Type of the policy</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.security.security.provider-policy`](io.helidon.security.Security.md#provider-policy)

---

See the [manifest](manifest.md) for all available types.
