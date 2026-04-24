# io.helidon.integrations.oci.OciConfig

## Description

Meta configuration of OCI integration for Helidon

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
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>allowed-authentication-methods</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of attempted authentication strategies in case <code>io.helidon.integrations.oci.OciConfig#authenticationMethod()</code> is set to <code>#AUTHENTICATION_METHOD_AUTO</code></td>
</tr>
<tr>
<td>
<a id="authentication"></a>
<a href="io.helidon.helidon.oci.AuthenticationConfig.md">
<code>authentication</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for authentication</td>
</tr>
<tr>
<td>
<code>authentication-method</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">auto</code>
</td>
<td>Authentication method to use</td>
</tr>
<tr>
<td>
<code>authentication-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT10S</code>
</td>
<td>Timeout of authentication operations, where applicable</td>
</tr>
<tr>
<td>
<code>federation-endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>Customization of federation endpoint for authentication providers</td>
</tr>
<tr>
<td>
<code>imds-base-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The OCI IMDS URI (http URL pointing to the metadata service, if customization needed)</td>
</tr>
<tr>
<td>
<code>imds-detect-retries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Customize the number of retries to contact IMDS service</td>
</tr>
<tr>
<td>
<code>imds-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>The OCI IMDS connection timeout</td>
</tr>
<tr>
<td>
<code>region</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OciConfigSupport">OciConfigSupport</code>
</td>
<td class="cm-default-cell">
</td>
<td>Explicit region</td>
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
<td>OCI tenant id for Instance Principal, Resource Principal or OKE Workload</td>
</tr>
</tbody>
</table>



## Usages

- [`helidon.oci`](io.helidon.HelidonConfig.md#oci)

---

See the [manifest](manifest.md) for all available types.
