# io.helidon.security.providers.IdcsRoleMapperConfig

## Description

Merged configuration for security.providers.idcs-role-mapper

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<a id="cache-config"></a>
<a href="io.helidon.security.providers.common.EvictableCache.md">
<code>cache-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="EvictableCache">EvictableCache</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit <code>io.helidon.security.providers.common.EvictableCache</code> for role caching</td>
</tr>
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
<a id="idcs-app-name-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-app-name-handler</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure token handler for IDCS Application name</td>
</tr>
<tr>
<td>
<a id="idcs-tenant-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-tenant-handler</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TokenHandler">TokenHandler</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure token handler for IDCS Tenant ID</td>
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



## Merged Types

- [io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md)
- [io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider](io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md)

## Usages

- [`security.providers.idcs-role-mapper`](io.helidon.security.spi.SecurityProvider.md#idcs-role-mapper)

---

See the [manifest](manifest.md) for all available types.
