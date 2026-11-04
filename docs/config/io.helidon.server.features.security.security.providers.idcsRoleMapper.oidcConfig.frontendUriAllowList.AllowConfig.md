# io.<wbr>helidon.<wbr>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs<wbr>Role<wbr>Mapper.<wbr>oidc<wbr>Config.<wbr>frontend<wbr>UriAllow<wbr>List.<wbr>Allow<wbr>Config

## Description

Configuration for server.features.security.security.providers.idcs-role-mapper.oidc-config.frontend-uri-allow-list.allow

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
<code>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Allows all strings to match (subject to "deny" conditions)</td>
</tr>
<tr>
<td>
<code>exact</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Exact strings to allow</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td>
<code>List&lt;<wbr>Pattern&gt;</code>
</td>
<td>
</td>
<td><code>Pattern</code>s specifying strings to allow</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Prefixes specifying strings to allow</td>
</tr>
<tr>
<td>
<code>suffix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Suffixes specifying strings to allow</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.configurable.AllowList.md#allow"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>frontend-<wbr>uri-<wbr>allow-<wbr>list.<wbr>allow</code></a>

---

See the [manifest](manifest.md) for all available types.
