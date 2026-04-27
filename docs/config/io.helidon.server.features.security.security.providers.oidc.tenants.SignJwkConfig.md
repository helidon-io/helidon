# io.helidon.server.features.security.security.providers.oidc.tenants.SignJwkConfig

## Description

Configuration for server.features.security.security.providers.oidc.tenants.sign-jwk

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<a id="resource"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>resource</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td>A resource pointing to JWK with public keys of signing certificates used to validate JWT</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.security.security.providers.oidc.tenants.sign-jwk`](io.helidon.security.providers.oidc.common.TenantConfig.md#sign-jwk)

---

See the [manifest](manifest.md) for all available types.
