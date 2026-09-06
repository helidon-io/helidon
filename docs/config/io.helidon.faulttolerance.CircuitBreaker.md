# io.<wbr>helidon.<wbr>faulttolerance.<wbr>Circuit<wbr>Breaker

## Description

Configuration of a circuit breaker

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
<code>volume</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10</code>
</td>
<td>Rolling window size used to calculate ratio of failed requests</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5S</code>
</td>
<td>How long to wait before transitioning from open to half-open state</td>
</tr>
<tr>
<td>
<code>error-<wbr>ratio</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>60</code>
</td>
<td>How many failures out of 100 will trigger the circuit to open</td>
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
<code>success-<wbr>threshold</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1</code>
</td>
<td>How many successful calls will close a half-open circuit</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.FaultToleranceConfig.md#circuit-breakers"><code>fault-<wbr>tolerance.<wbr>circuit-<wbr>breakers</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.JwkLoaderConfig.md#circuit-breaker"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.security.providers.idcsRoleMapper.oidcConfig.tenants.JwkLoaderConfig.md#circuit-breaker"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.security.providers.jwt.JwkLoaderConfig.md#circuit-breaker"><code>security.<wbr>providers.<wbr>jwt.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.security.providers.oidc.JwkLoaderConfig.md#circuit-breaker"><code>security.<wbr>providers.<wbr>oidc.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.security.providers.oidc.tenants.JwkLoaderConfig.md#circuit-breaker"><code>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.JwkLoaderConfig.md#circuit-breaker"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.server.features.security.security.providers.idcsRoleMapper.oidcConfig.tenants.JwkLoaderConfig.md#circuit-breaker"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.server.features.security.security.providers.jwt.JwkLoaderConfig.md#circuit-breaker"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.JwkLoaderConfig.md#circuit-breaker"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>
- <a href="io.helidon.server.features.security.security.providers.oidc.tenants.JwkLoaderConfig.md#circuit-breaker"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>tenants.<wbr>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code></a>

---

See the [manifest](manifest.md) for all available types.
