# io.<wbr>helidon.<wbr>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>Idcs<wbr>Role<wbr>Mapper<wbr>Config

## Description

Merged configuration for server.features.security.security.providers.idcs-role-mapper

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
<a id="cache-config"></a>
<a href="io.helidon.security.providers.common.EvictableCache.md">
<code>cache-<wbr>config</code>
</a>
</td>
<td>
<code>Evictable<wbr>Cache</code>
</td>
<td>
</td>
<td>Use explicit <code>io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>common.<wbr>Evictable<wbr>Cache</code> for role caching</td>
</tr>
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
<a id="idcs-app-name-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-<wbr>app-<wbr>name-<wbr>handler</code>
</a>
</td>
<td>
<code>Token<wbr>Handler</code>
</td>
<td>
</td>
<td>Configure token handler for IDCS Application name</td>
</tr>
<tr>
<td>
<a id="idcs-tenant-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>idcs-<wbr>tenant-<wbr>handler</code>
</a>
</td>
<td>
<code>Token<wbr>Handler</code>
</td>
<td>
</td>
<td>Token handler for an IDCS tenant ID. The extracted tenant ID must be a single DNS label: 1 to 63 alphanumeric or hyphen characters, with no leading or trailing hyphen. Invalid tenant IDs fail before endpoint resolution</td>
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



## Merged Types

- [io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>idcs.<wbr>mapper.<wbr>Idcs<wbr>MtRole<wbr>Mapper<wbr>Provider](io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.md)
- [io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>idcs.<wbr>mapper.<wbr>Idcs<wbr>Role<wbr>Mapper<wbr>Provider](io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.md)

## Usages

- <a href="io.helidon.security.spi.SecurityProvider.md#idcs-role-mapper"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper</code></a>

---

See the [manifest](manifest.md) for all available types.
