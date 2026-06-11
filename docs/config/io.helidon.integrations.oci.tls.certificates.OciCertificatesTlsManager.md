# io.helidon.integrations.oci.tls.certificates.OciCertificatesTlsManager

## Description

Blueprint configuration for <code>Oci<wbr>Certificates<wbr>TlsManager</code>

## Configuration options


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
<code>vault-<wbr>crypto-<wbr>endpoint</code>
</td>
<td>
<code>URI</code>
</td>
<td>The address to use for the OCI Key Management Service / Vault crypto usage</td>
</tr>
<tr>
<td>
<code>schedule</code>
</td>
<td>
<code>String</code>
</td>
<td>The schedule for trigger a reload check, testing whether there is a new <code>io.<wbr>helidon.<wbr>common.<wbr>tls.<wbr>Tls</code> instance available</td>
</tr>
<tr>
<td>
<code>ca-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>The Certificate Authority OCID</td>
</tr>
<tr>
<td>
<code>key-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>The Key OCID</td>
</tr>
<tr>
<td>
<code>compartment-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>The OCID of the compartment the services are in</td>
</tr>
<tr>
<td>
<code>key-<wbr>password</code>
</td>
<td>
<code>Supplier</code>
</td>
<td>The Key password</td>
</tr>
<tr>
<td>
<code>cert-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>The Certificate OCID</td>
</tr>
<tr>
<td>
<code>vault-<wbr>management-<wbr>endpoint</code>
</td>
<td>
<code>URI</code>
</td>
<td>The address to use for the OCI Key Management Service / Vault management usage</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
