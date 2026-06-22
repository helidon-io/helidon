# io.<wbr>helidon.<wbr>cors.<wbr>Cross<wbr>Origin<wbr>Config

## Description

Represents information about cross origin request sharing

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
<code>allow-<wbr>headers</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Sets the allow headers</td>
</tr>
<tr>
<td>
<code>allow-<wbr>credentials</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Sets the allow credentials flag</td>
</tr>
<tr>
<td>
<code>max-<wbr>age-<wbr>seconds</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>3600</code>
</td>
<td>Sets the maximum age</td>
</tr>
<tr>
<td>
<code>allow-<wbr>origins</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Sets the allowOrigins</td>
</tr>
<tr>
<td>
<code>expose-<wbr>headers</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Sets the expose headers</td>
</tr>
<tr>
<td>
<code>path-<wbr>pattern</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>{+}</code>
</td>
<td>Updates the path prefix for this cross-origin config</td>
</tr>
<tr>
<td>
<code>allow-<wbr>methods</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>*</code>
</td>
<td>Sets the allow methods</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Sets whether this config should be enabled or not</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.openapi.OpenApiFeature.md#cors"><code>openapi.<wbr>cors</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#cors"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>cors</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#cors"><code>security.<wbr>providers.<wbr>oidc.<wbr>cors</code></a>
- <a href="io.helidon.webserver.observe.ObserveFeature.md#cors"><code>server.<wbr>features.<wbr>observe.<wbr>cors</code></a>
- <a href="io.helidon.openapi.OpenApiFeature.md#cors"><code>server.<wbr>features.<wbr>openapi.<wbr>cors</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#cors"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>cors</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#cors"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>cors</code></a>

---

See the [manifest](manifest.md) for all available types.
