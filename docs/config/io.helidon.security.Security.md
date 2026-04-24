# io.helidon.security.Security

## Description

Configuration of security providers, integration and other security options

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
<code>default-authentication-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>ID of the default authentication provider</td>
</tr>
<tr>
<td>
<code>default-authorization-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>ID of the default authorization provider</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Security can be disabled using configuration, or explicitly</td>
</tr>
<tr>
<td>
<a id="environment"></a>
<a href="io.helidon.security.EnvironmentConfig.md">
<code>environment</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for environment</td>
</tr>
<tr>
<td>
<a id="provider-policy"></a>
<a href="io.helidon.security.ProviderPolicyConfig.md">
<code>provider-policy</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for provider-policy</td>
</tr>
<tr>
<td>
<a id="providers"></a>
<a href="io.helidon.security.spi.SecurityProvider.md">
<code>providers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;SecurityProvider&gt;">List&lt;SecurityProvider&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add a provider, works as <code>#addProvider(io.helidon.security.spi.SecurityProvider, String)</code>, where the name is set to <code>Class#getSimpleName()</code></td>
</tr>
<tr>
<td>
<a id="secrets"></a>
<a href="io.helidon.security.SecretsConfig.md">
<code>secrets</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Config&gt;">List&lt;Config&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configured secrets</td>
</tr>
<tr>
<td>
<a id="tracing"></a>
<a href="io.helidon.security.TracingConfig.md">
<code>tracing</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for tracing</td>
</tr>
</tbody>
</table>



## Usages

- [`security`](config_reference.md#security)
- [`server.features.security.security`](io.helidon.webserver.security.SecurityFeature.md#security)

---

See the [manifest](manifest.md) for all available types.
