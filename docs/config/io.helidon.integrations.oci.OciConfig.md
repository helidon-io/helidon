# io.helidon.integrations.oci.OciConfig

## Description

Meta configuration of OCI integration for Helidon

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>allowed-authentication-methods</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of attempted authentication strategies in case &lt;code&gt;io.helidon.integrations.oci.OciConfig#authenticationMethod()&lt;/code&gt; is set to &lt;code&gt;#AUTHENTICATION_METHOD_AUTO&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="authentication"></a><a href="io.helidon.helidon.oci.AuthenticationConfig.md"><code>authentication</code></a></td>
<td></td>
<td></td>
<td>Configuration for authentication</td>
</tr>
<tr>
<td><code>authentication-method</code></td>
<td><code>String</code></td>
<td><code>auto</code></td>
<td>Authentication method to use</td>
</tr>
<tr>
<td><code>authentication-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT10S</code></td>
<td>Timeout of authentication operations, where applicable</td>
</tr>
<tr>
<td><code>federation-endpoint</code></td>
<td><code>URI</code></td>
<td></td>
<td>Customization of federation endpoint for authentication providers</td>
</tr>
<tr>
<td><code>imds-base-uri</code></td>
<td><code>URI</code></td>
<td></td>
<td>The OCI IMDS URI (http URL pointing to the metadata service, if customization needed)</td>
</tr>
<tr>
<td><code>imds-detect-retries</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Customize the number of retries to contact IMDS service</td>
</tr>
<tr>
<td><code>imds-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT1S</code></td>
<td>The OCI IMDS connection timeout</td>
</tr>
<tr>
<td><code>region</code></td>
<td><code>OciConfigSupport</code></td>
<td></td>
<td>Explicit region</td>
</tr>
<tr>
<td><code>tenant-id</code></td>
<td><code>String</code></td>
<td></td>
<td>OCI tenant id for Instance Principal, Resource Principal or OKE Workload</td>
</tr>
</tbody>
</table>


## Usages

- [`helidon.oci`](io.helidon.HelidonConfig.md#oci)

---

See the [manifest](manifest.md) for all available types.
