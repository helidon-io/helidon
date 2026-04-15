# io.helidon.integrations.oci.sdk.runtime.OciConfig

## Description

Configuration used by &lt;code&gt;OciAuthenticationDetailsProvider&lt;/code&gt;

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
<td><a id="auth"></a><a href="io.helidon.oci.AuthConfig.md"><code>auth</code></a></td>
<td></td>
<td>Configuration for auth</td>
</tr>
<tr>
<td><a id="auth-strategies"></a><a href="io.helidon.integrations.oci.sdk.runtime.OciConfigAuthStrategies.md"><code>auth-strategies</code></a></td>
<td><code>List&lt;OciConfigAuthStrategies&gt;</code></td>
<td>The list of authentication strategies that will be attempted by &lt;code&gt;com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider&lt;/code&gt; when one is called for</td>
</tr>
<tr>
<td><a id="auth-strategy"></a><a href="io.helidon.integrations.oci.sdk.runtime.OciConfigAuthStrategy.md"><code>auth-strategy</code></a></td>
<td><code>OciConfigAuthStrategy</code></td>
<td>The singular authentication strategy to apply</td>
</tr>
<tr>
<td><a id="config"></a><a href="io.helidon.oci.ConfigConfig.md"><code>config</code></a></td>
<td></td>
<td>Configuration for config</td>
</tr>
<tr>
<td><a id="imds"></a><a href="io.helidon.oci.ImdsConfig.md"><code>imds</code></a></td>
<td></td>
<td>Configuration for imds</td>
</tr>
</tbody>
</table>


## Usages

- [`oci`](config_reference.md#oci)

---

See the [manifest](manifest.md) for all available types.
