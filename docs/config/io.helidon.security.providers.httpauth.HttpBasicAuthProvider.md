# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>httpauth.<wbr>Http<wbr>Basic<wbr>Auth<wbr>Provider

## Description

HTTP Basic Authentication provider

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
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundTarget.md">
<code>outbound</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Outbound<wbr>Target&gt;</code>
</td>
<td>
</td>
<td>Add a new outbound target to configure identity propagation or explicit username/password</td>
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
<code>realm</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>helidon</code>
</td>
<td>Set the realm to use when challenging users</td>
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
<a id="users"></a>
<a href="io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser.md">
<code>users</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Config<wbr>User&gt;</code>
</td>
<td>
</td>
<td>Set user store to validate users</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.spi.SecurityProvider.md#http-basic-auth"><code>security.<wbr>providers.<wbr>http-<wbr>basic-<wbr>auth</code></a>
- <a href="io.helidon.security.spi.SecurityProvider.md#http-basic-auth"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>http-<wbr>basic-<wbr>auth</code></a>

---

See the [manifest](manifest.md) for all available types.
