# io.<wbr>helidon.<wbr>integrations.<wbr>oci.<wbr>tls.<wbr>certificates.<wbr>OciCertificate<wbr>Bundle<wbr>TlsManager

## Description

Blueprint configuration for <code>Oci<wbr>Certificate<wbr>Bundle<wbr>TlsManager</code>

## Configuration options


<table>
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
<code>schedule</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The schedule for checking whether OCI has newer TLS material</td>
</tr>
<tr>
<td>
<code>ca-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The certificate authority OCID</td>
</tr>
<tr>
<td>
<code>always-<wbr>reload</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to reload TLS even when the certificate version and CA certificate are unchanged</td>
</tr>
<tr>
<td>
<code>cert-<wbr>ocid</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The OCI-managed certificate OCID</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>clients.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>server.<wbr>sockets.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>
- <a href="io.helidon.common.tls.TlsManager.md#oci-certificate-bundle-tls-manager"><code>server.<wbr>tls.<wbr>manager.<wbr>oci-<wbr>certificate-<wbr>bundle-<wbr>tls-<wbr>manager</code></a>

---

See the [manifest](manifest.md) for all available types.
