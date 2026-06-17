# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>idcs.<wbr>mapper.<wbr>Idcs<wbr>Role<wbr>Mapper<wbr>Provider<wbr>Base.<wbr>Builder

## Description

Fluent API builder for <code>Idcs<wbr>Role<wbr>Mapper<wbr>Provider<wbr>Base</code>

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
<code>default-<wbr>idcs-<wbr>subject-<wbr>type</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>user</code>
</td>
<td>Configure subject type to use when requesting roles from IDCS</td>
</tr>
<tr>
<td>
<a id="oidc-config"></a>
<a href="io.helidon.security.providers.oidc.common.OidcConfig.md">
<code>oidc-<wbr>config</code>
</a>
</td>
<td>
<code>Oidc<wbr>Config</code>
</td>
<td>
</td>
<td>Use explicit <code>io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>common.<wbr>Oidc<wbr>Config</code> instance, e.g</td>
</tr>
<tr>
<td>
<a id="subject-types"></a>
<a href="io.helidon.security.SubjectType.md">
<code>subject-<wbr>types</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Subject<wbr>Type&gt;</code>
</td>
<td>
<code>USER</code>
</td>
<td>Add a supported subject type</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>idcs.<wbr>mapper.<wbr>Idcs<wbr>MtRole<wbr>Mapper<wbr>Provider](io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md)
- [io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>idcs.<wbr>mapper.<wbr>Idcs<wbr>Role<wbr>Mapper<wbr>Provider](io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md)

---

See the [manifest](manifest.md) for all available types.
