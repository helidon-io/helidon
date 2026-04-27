# io.helidon.security.providers.config.vault.ConfigVaultProvider.SecretConfig

## Description

Provider of secrets defined in configuration itself

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
<code>value</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Value of the secret, can be a reference to another configuration key, such as ${app.secret}</td>
</tr>
</tbody>
</table>



## Usages

- [`security.secrets.config.config-vault`](io.helidon.security.SecretsProviderConfig.md#config-vault)
- [`server.features.security.security.secrets.config.config-vault`](io.helidon.security.SecretsProviderConfig.md#config-vault)

---

See the [manifest](manifest.md) for all available types.
