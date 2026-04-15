# io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase.Builder

## Description

Fluent API builder for &lt;code&gt;IdcsRoleMapperProviderBase&lt;/code&gt;

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
<td><code>default-idcs-subject-type</code></td>
<td><code>String</code></td>
<td><code>user</code></td>
<td>Configure subject type to use when requesting roles from IDCS</td>
</tr>
<tr>
<td><a id="oidc-config"></a><a href="io.helidon.security.providers.oidc.common.OidcConfig.md"><code>oidc-config</code></a></td>
<td><code>OidcConfig</code></td>
<td></td>
<td>Use explicit &lt;code&gt;io.helidon.security.providers.oidc.common.OidcConfig&lt;/code&gt; instance, e.g</td>
</tr>
<tr>
<td><a id="subject-types"></a><a href="io.helidon.security.SubjectType.md"><code>subject-types</code></a></td>
<td><code>List&lt;SubjectType&gt;</code></td>
<td><code>USER</code></td>
<td>Add a supported subject type</td>
</tr>
</tbody>
</table>


## Dependent Types

- [io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md)
- [io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md)

---

See the [manifest](manifest.md) for all available types.
