# io.helidon.oci.AuthConfig

## Description

Configuration for oci.auth

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
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>fingerprint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI authentication fingerprint</td>
</tr>
<tr>
<td>
<code>keyFile</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="oci_api_key.pem">oci_api_key.pem</code>
</td>
<td>The OCI authentication key file</td>
</tr>
<tr>
<td>
<code>passphrase</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI authentication passphrase</td>
</tr>
<tr>
<td>
<code>private-key</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI authentication private key</td>
</tr>
<tr>
<td>
<code>private-key-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI authentication key file path</td>
</tr>
<tr>
<td>
<code>region</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-default-cell">
</td>
<td>The OCI tenant id</td>
</tr>
<tr>
<td>
<code>user-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI user id</td>
</tr>
</tbody>
</table>



## Usages

- [`oci.auth`](io.helidon.integrations.oci.sdk.runtime.OciConfig.md#auth)

---

See the [manifest](manifest.md) for all available types.
