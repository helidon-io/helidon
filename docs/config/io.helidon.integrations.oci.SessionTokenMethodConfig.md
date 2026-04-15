# io.helidon.integrations.oci.SessionTokenMethodConfig

## Description

Configuration of the &lt;code&gt;config&lt;/code&gt; authentication method

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
<td><code>refresh-period</code></td>
<td><code>Duration</code></td>
<td>Refresh period, i.e</td>
</tr>
<tr>
<td><code>initial-refresh-delay</code></td>
<td><code>Duration</code></td>
<td>Delay of the first refresh</td>
</tr>
<tr>
<td><code>user-id</code></td>
<td><code>String</code></td>
<td>The OCI user id</td>
</tr>
<tr>
<td><code>fingerprint</code></td>
<td><code>String</code></td>
<td>The OCI authentication fingerprint</td>
</tr>
<tr>
<td><code>passphrase</code></td>
<td><code>String</code></td>
<td>The OCI authentication passphrase</td>
</tr>
<tr>
<td><code>region</code></td>
<td><code>String</code></td>
<td>The OCI region</td>
</tr>
<tr>
<td><code>session-token</code></td>
<td><code>String</code></td>
<td>Session token value</td>
</tr>
<tr>
<td><code>private-key-path</code></td>
<td><code>Path</code></td>
<td>The OCI authentication private key resource</td>
</tr>
<tr>
<td><code>session-lifetime-hours</code></td>
<td><code>Long</code></td>
<td>Maximal lifetime of a session</td>
</tr>
<tr>
<td><code>session-token-path</code></td>
<td><code>Path</code></td>
<td>Session token path</td>
</tr>
<tr>
<td><code>tenant-id</code></td>
<td><code>String</code></td>
<td>The OCI tenant id</td>
</tr>
</tbody>
</table>


## Usages

- [`helidon.oci.authentication.session-token`](io.helidon.helidon.oci.AuthenticationConfig.md#session-token)

---

See the [manifest](manifest.md) for all available types.
