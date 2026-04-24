# io.helidon.security.providers.httpauth.ConfigUserStore.ConfigUser

## Description

A user that is loaded from configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>User's password</td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>List of roles the user is in</td>
</tr>
<tr>
<td>
<code>login</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>User's login</td>
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
