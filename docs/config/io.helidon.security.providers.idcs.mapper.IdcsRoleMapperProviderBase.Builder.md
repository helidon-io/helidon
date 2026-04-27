# io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase.Builder

## Description

Fluent API builder for <code>IdcsRoleMapperProviderBase</code>

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
<code>default-idcs-subject-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">user</code>
</td>
<td>Configure subject type to use when requesting roles from IDCS</td>
</tr>
<tr>
<td>
<a id="oidc-config"></a>
<a href="io.helidon.security.providers.oidc.common.OidcConfig.md">
<code>oidc-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">OidcConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.oidc.common.OidcConfig</code> instance, e.g</td>
</tr>
<tr>
<td>
<a id="subject-types"></a>
<a href="io.helidon.security.SubjectType.md">
<code>subject-types</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;SubjectType&gt;">List&lt;SubjectType&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">USER</code>
</td>
<td>Add a supported subject type</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md)
- [io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md)

---

See the [manifest](manifest.md) for all available types.
