# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>JwtProvider

## Description

JWT authentication provider

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
<code>allow-<wbr>impersonation</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to allow impersonation by explicitly overriding username from outbound requests using <code>io.<wbr>helidon.<wbr>security.<wbr>Endpoint<wbr>Config#<wbr>PROPERTY_<wbr>OUTBOUND_<wbr>ID</code> property</td>
</tr>
<tr>
<td>
<code>allow-<wbr>unsigned</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Configure support for unsigned JWTs without requiring verification JWKs</td>
</tr>
<tr>
<td>
<a id="atn-token-handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>atn-<wbr>token.<wbr>handler</code>
</a>
</td>
<td>
<code>Token<wbr>Handler</code>
</td>
<td>
</td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td>
<a id="atn-token-jwk-resource"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>atn-<wbr>token.<wbr>jwk.<wbr>resource</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>JWK resource used to verify JWTs created by other parties</td>
</tr>
<tr>
<td>
<code>atn-<wbr>token.<wbr>jwt-<wbr>audience</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Audience expected in inbound JWTs</td>
</tr>
<tr>
<td>
<code>atn-<wbr>token.<wbr>jwt-<wbr>issuer</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Issuer expected in inbound JWTs</td>
</tr>
<tr>
<td>
<code>atn-<wbr>token.<wbr>verify-<wbr>signature</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Configure whether to verify signatures</td>
</tr>
<tr>
<td>
<code>authenticate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to authenticate requests</td>
</tr>
<tr>
<td>
<a id="jwk-loader-circuit-breaker"></a>
<a href="io.helidon.faulttolerance.CircuitBreaker.md">
<code>jwk-<wbr>loader.<wbr>circuit-<wbr>breaker</code>
</a>
</td>
<td>
<code>Circuit<wbr>Breaker</code>
</td>
<td>
</td>
<td>Circuit breaker around each complete retry batch used to load verification keys from a filesystem path or URI; by default, the circuit opens after one exhausted batch and permits a recovery probe after 5 seconds</td>
</tr>
<tr>
<td>
<a id="jwk-loader-retry"></a>
<a href="io.helidon.faulttolerance.Retry.md">
<code>jwk-<wbr>loader.<wbr>retry</code>
</a>
</td>
<td>
<code>Retry</code>
</td>
<td>
</td>
<td>Retry used when loading verification keys from a filesystem path or URI; by default, it wraps two timeout-guarded attempts within an 11-second overall timeout</td>
</tr>
<tr>
<td>
<a id="jwk-loader-timeout"></a>
<a href="io.helidon.faulttolerance.Timeout.md">
<code>jwk-<wbr>loader.<wbr>timeout</code>
</a>
</td>
<td>
<code>Timeout</code>
</td>
<td>
</td>
<td>Timeout applied to each attempt to load verification keys from a filesystem path or URI; it defaults to 5 seconds, must be positive, must execute on the current thread, and must not exceed the retry overall timeout</td>
</tr>
<tr>
<td>
<code>jwt-<wbr>groups-<wbr>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>groups</code>
</td>
<td>Path to the JWT payload claim containing the groups to add as role grants</td>
</tr>
<tr>
<td>
<code>jwt-<wbr>groups-<wbr>separator</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Separator used to split a string claim value into multiple groups</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-<wbr>type</code>
</a>
</td>
<td>
<code>Subject<wbr>Type</code>
</td>
<td>
<code>USER</code>
</td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td>
<code>propagate</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to propagate identity</td>
</tr>
<tr>
<td>
<a id="sign-token"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>sign-<wbr>token</code>
</a>
</td>
<td>
<code>Outbound<wbr>Config</code>
</td>
<td>
</td>
<td>Configuration of outbound rules</td>
</tr>
<tr>
<td>
<a id="sign-token-jwk-resource"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>sign-<wbr>token.<wbr>jwk.<wbr>resource</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>JWK resource used to sign JWTs created by us</td>
</tr>
<tr>
<td>
<code>sign-<wbr>token.<wbr>jwt-<wbr>issuer</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Issuer used to create new JWTs</td>
</tr>
<tr>
<td>
<code>use-<wbr>jwt-<wbr>groups</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Claim <code>groups</code> from JWT will be used to automatically add  groups to current subject (may be used with <code>jakarta.<wbr>annotation.<wbr>security.<wbr>Roles<wbr>Allowed</code> annotation)</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.spi.SecurityProvider.md#jwt"><code>security.<wbr>providers.<wbr>jwt</code></a>
- <a href="io.helidon.security.spi.SecurityProvider.md#jwt"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt</code></a>

---

See the [manifest](manifest.md) for all available types.
