# io.helidon.integrations.oci.tls.certificates.OciCertificatesTlsManager

## Description

Blueprint configuration for &lt;code&gt;OciCertificatesTlsManager&lt;/code&gt;

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
<td><code>vault-crypto-endpoint</code></td>
<td><code>URI</code></td>
<td>The address to use for the OCI Key Management Service / Vault crypto usage</td>
</tr>
<tr>
<td><code>schedule</code></td>
<td><code>String</code></td>
<td>The schedule for trigger a reload check, testing whether there is a new &lt;code&gt;io.helidon.common.tls.Tls&lt;/code&gt; instance available</td>
</tr>
<tr>
<td><code>ca-ocid</code></td>
<td><code>String</code></td>
<td>The Certificate Authority OCID</td>
</tr>
<tr>
<td><code>key-ocid</code></td>
<td><code>String</code></td>
<td>The Key OCID</td>
</tr>
<tr>
<td><code>compartment-ocid</code></td>
<td><code>String</code></td>
<td>The OCID of the compartment the services are in</td>
</tr>
<tr>
<td><code>key-password</code></td>
<td><code>Supplier</code></td>
<td>The Key password</td>
</tr>
<tr>
<td><code>cert-ocid</code></td>
<td><code>String</code></td>
<td>The Certificate OCID</td>
</tr>
<tr>
<td><code>vault-management-endpoint</code></td>
<td><code>URI</code></td>
<td>The address to use for the OCI Key Management Service / Vault management usage</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
