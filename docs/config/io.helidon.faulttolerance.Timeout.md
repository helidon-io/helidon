# io.<wbr>helidon.<wbr>faulttolerance.<wbr>Timeout

## Description

<code>Timeout</code> configuration bean

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
<code>current-<wbr>thread</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Flag to indicate that code must be executed in current thread instead of in an executor's thread</td>
</tr>
<tr>
<td>
<code>enable-<wbr>metrics</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Duration to wait before timing out</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.FaultToleranceConfig.md#timeouts"><code>fault-<wbr>tolerance.<wbr>timeouts</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.JwkLoaderConfig.md#timeout"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.JwkLoaderConfig.md#timeout"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.security.providers.jwt.JwkLoaderConfig.md#timeout"><code>security.<wbr>providers.<wbr>jwt.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.security.providers.oidc.JwkLoaderConfig.md#timeout"><code>security.<wbr>providers.<wbr>oidc.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.security.providers.oidc.tenants.JwkLoaderConfig.md#timeout"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.JwkLoaderConfig.md#timeout"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.JwkLoaderConfig.md#timeout"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.server.features.security.security.providers.jwt.JwkLoaderConfig.md#timeout"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.JwkLoaderConfig.md#timeout"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.tenants.JwkLoaderConfig.md#timeout"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>timeout</code></a>

---

See the [manifest](manifest.md) for all available types.
