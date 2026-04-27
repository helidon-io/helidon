# io.helidon.integrations.oci.ConfigMethodConfig

## Description

Configuration of the <code>config</code> authentication method

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
<a id="private-key"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>private-key</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td>The OCI authentication private key resource</td>
</tr>
<tr>
<td>
<code>user-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The OCI user id</td>
</tr>
<tr>
<td>
<code>fingerprint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The OCI authentication fingerprint</td>
</tr>
<tr>
<td>
<code>passphrase</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The OCI authentication passphrase</td>
</tr>
<tr>
<td>
<code>region</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The OCI region</td>
</tr>
<tr>
<td>
<code>tenant-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The OCI tenant id</td>
</tr>
</tbody>
</table>



## Usages

- [`helidon.oci.authentication.config`](io.helidon.helidon.oci.AuthenticationConfig.md#config)

---

See the [manifest](manifest.md) for all available types.
