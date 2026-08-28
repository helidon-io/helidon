# io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>SniMode

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>URI_<wbr>HOST</code></td>
<td>Use the resolved request URI host</td>
</tr>
<tr>
<td><code>HOST_<wbr>HEADER</code></td>
<td>Use the final outbound HTTP authority from the <code>Host</code> header</td>
</tr>
<tr>
<td><code>EXPLICIT</code></td>
<td>Use the explicitly configured SNI host</td>
</tr>
<tr>
<td><code>DISABLED</code></td>
<td>Clear the SNI server name</td>
</tr>
</tbody>
</table>

## Usages

- <a href="io.helidon.webclient.api.SniConfig.md#mode"><code>clients.<wbr>sni.<wbr>mode</code></a>
- <a href="io.helidon.webclient.api.SniConfig.md#mode"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>sni.<wbr>mode</code></a>
- <a href="io.helidon.webclient.api.SniConfig.md#mode"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>sni.<wbr>mode</code></a>
- <a href="io.helidon.webclient.api.SniConfig.md#mode"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>sni.<wbr>mode</code></a>
- <a href="io.helidon.webclient.api.SniConfig.md#mode"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>sni.<wbr>mode</code></a>

---

See the [manifest](manifest.md) for all available types.
