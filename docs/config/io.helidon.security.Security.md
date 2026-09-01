# io.<wbr>helidon.<wbr>security.<wbr>Security

## Description

Configuration of security providers, integration and other security options

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
<code>default-<wbr>authentication-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>ID of the default authentication provider</td>
</tr>
<tr>
<td>
<code>default-<wbr>authorization-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>ID of the default authorization provider</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
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
<td>
</td>
<td>
</td>
<td>Configuration for environment</td>
</tr>
<tr>
<td>
<a id="provider-policy"></a>
<a href="io.helidon.security.ProviderPolicyConfig.md">
<code>provider-<wbr>policy</code>
</a>
</td>
<td>
</td>
<td>
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
<td>
<code>List&lt;<wbr>Security<wbr>Provider&gt;</code>
</td>
<td>
</td>
<td>Add a provider, works as <code>#add<wbr>Provider(<wbr>io.helidon.<wbr>security.<wbr>spi.<wbr>Security<wbr>Provider,<wbr> String)</code>, where the name is set to <code>Class#<wbr>getSimple<wbr>Name(<wbr>)</code></td>
</tr>
<tr>
<td>
<a id="secrets"></a>
<a href="io.helidon.security.SecretsConfig.md">
<code>secrets</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Config&gt;</code>
</td>
<td>
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
<td>
</td>
<td>
</td>
<td>Configuration for tracing</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#security"><code>security</code></a>
- <a href="io.helidon.webserver.security.SecurityFeature.md#security"><code>server.<wbr>features.<wbr>security.<wbr>security</code></a>

---

See the [manifest](manifest.md) for all available types.
