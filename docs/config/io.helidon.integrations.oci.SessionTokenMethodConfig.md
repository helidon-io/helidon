# io.helidon.integrations.oci.SessionTokenMethodConfig

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
<code>refresh-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Refresh period, i.e</td>
</tr>
<tr>
<td>
<code>initial-refresh-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td>Delay of the first refresh</td>
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
<code>session-token</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Session token value</td>
</tr>
<tr>
<td>
<code>private-key-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td>The OCI authentication private key resource</td>
</tr>
<tr>
<td>
<code>session-lifetime-hours</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>Maximal lifetime of a session</td>
</tr>
<tr>
<td>
<code>session-token-path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Path</code>
</td>
<td>Session token path</td>
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

- [`helidon.oci.authentication.session-token`](io.helidon.helidon.oci.AuthenticationConfig.md#session-token)

---

See the [manifest](manifest.md) for all available types.
