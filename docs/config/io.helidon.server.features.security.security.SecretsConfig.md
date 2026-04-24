# io.helidon.server.features.security.security.SecretsConfig

## Description

Configuration for server.features.security.security.secrets

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
<a id="config"></a>
<a href="io.helidon.security.SecretsProviderConfig.md">
<code>config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SecretsProviderConfig">SecretsProviderConfig</code>
</td>
<td>Configuration specific to the secret provider</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Name of the secret, used for lookup</td>
</tr>
<tr>
<td>
<code>provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Name of the secret provider</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.security.security.secrets`](io.helidon.security.Security.md#secrets)

---

See the [manifest](manifest.md) for all available types.
