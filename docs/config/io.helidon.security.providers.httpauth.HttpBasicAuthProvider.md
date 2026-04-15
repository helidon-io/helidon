# io.helidon.security.providers.httpauth.HttpBasicAuthProvider

## Description

HTTP Basic Authentication provider

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="outbound"></a><a href="io.helidon.security.providers.common.OutboundTarget.md"><code>outbound</code></a></td>
<td><code>List&lt;OutboundTarget&gt;</code></td>
<td></td>
<td>Add a new outbound target to configure identity propagation or explicit username/password</td>
</tr>
<tr>
<td><code>optional</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether authentication is required</td>
</tr>
<tr>
<td><code>realm</code></td>
<td><code>String</code></td>
<td><code>helidon</code></td>
<td>Set the realm to use when challenging users</td>
</tr>
<tr>
<td><a id="principal-type"></a><a href="io.helidon.security.SubjectType.md"><code>principal-type</code></a></td>
<td><code>SubjectType</code></td>
<td><code>USER</code></td>
<td>Principal type this provider extracts (and also propagates)</td>
</tr>
<tr>
<td><a id="users"></a><a href="io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser.md"><code>users</code></a></td>
<td><code>List&lt;ConfigUser&gt;</code></td>
<td></td>
<td>Set user store to validate users</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.http-basic-auth`](io.helidon.security.spi.SecurityProvider.md#http-basic-auth)
- [`server.features.security.security.providers.http-basic-auth`](io.helidon.security.spi.SecurityProvider.md#http-basic-auth)

---

See the [manifest](manifest.md) for all available types.
