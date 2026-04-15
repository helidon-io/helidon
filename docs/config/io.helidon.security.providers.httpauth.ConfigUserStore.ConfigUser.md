# io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser

## Description

A user that is loaded from configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>password</code></td>
<td><code>String</code></td>
<td>User&#x27;s password</td>
</tr>
<tr>
<td><code>roles</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>List of roles the user is in</td>
</tr>
<tr>
<td><code>login</code></td>
<td><code>String</code></td>
<td>User&#x27;s login</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.http-basic-auth.users`](io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#users)
- [`security.providers.http-digest-auth.users`](io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md#users)
- [`server.features.security.security.providers.http-basic-auth.users`](io.helidon.security.providers.httpauth.HttpBasicAuthProvider.md#users)
- [`server.features.security.security.providers.http-digest-auth.users`](io.helidon.security.providers.httpauth.HttpDigestAuthProvider.md#users)

---

See the [manifest](manifest.md) for all available types.
