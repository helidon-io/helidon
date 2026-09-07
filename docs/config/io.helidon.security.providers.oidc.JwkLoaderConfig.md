# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>JwkLoader<wbr>Config

## Description

Configuration for security.providers.oidc.jwk-loader

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="circuit-breaker"></a>
<a href="io.helidon.faulttolerance.CircuitBreaker.md">
<code>circuit-<wbr>breaker</code>
</a>
</td>
<td>
<code>Circuit<wbr>Breaker</code>
</td>
<td>Circuit breaker around each complete retry batch used to load OIDC metadata and signing JWKs; by default, the circuit opens after one exhausted batch and permits a recovery probe after 5 seconds</td>
</tr>
<tr>
<td>
<a id="retry"></a>
<a href="io.helidon.faulttolerance.Retry.md">
<code>retry</code>
</a>
</td>
<td>
<code>Retry</code>
</td>
<td>Retry used while loading OIDC metadata and signing JWKs; by default, it wraps two timeout-guarded attempts within an 11-second overall timeout</td>
</tr>
<tr>
<td>
<a id="timeout"></a>
<a href="io.helidon.faulttolerance.Timeout.md">
<code>timeout</code>
</a>
</td>
<td>
<code>Timeout</code>
</td>
<td>Timeout applied to each attempt to load OIDC metadata and signing JWKs; it defaults to 5 seconds, must be positive, and must not exceed the retry overall timeout</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.oidc.OidcProvider.md#jwk-loader"><code>security.<wbr>providers.<wbr>oidc.<wbr>jwk-<wbr>loader</code></a>

---

See the [manifest](manifest.md) for all available types.
