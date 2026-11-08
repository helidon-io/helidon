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
<a id="environment-server-time"></a>
<a href="io.helidon.security.SecurityTime.md">
<code>environment.<wbr>server-<wbr>time</code>
</a>
</td>
<td>
<code>Security<wbr>Time</code>
</td>
<td>
</td>
<td>Server time to use when evaluating security policies that depend on time</td>
</tr>
<tr>
<td>
<code>provider-<wbr>policy.<wbr>class-<wbr>name</code>
</td>
<td>
<code>Class</code>
</td>
<td>
</td>
<td>Provider selection policy class name, only used when type is set to CLASS</td>
</tr>
<tr>
<td>
<a id="provider-policy-type"></a>
<a href="io.helidon.security.ProviderSelectionPolicyType.md">
<code>provider-<wbr>policy.<wbr>type</code>
</a>
</td>
<td>
<code>Provider<wbr>Selection<wbr>Policy<wbr>Type</code>
</td>
<td>
<code>FIRST</code>
</td>
<td>Type of the policy</td>
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
<code>secrets</code>
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
<a id="secrets-config"></a>
<a href="io.helidon.security.SecretsProviderConfig.md">
<code>secrets.<wbr>*.config</code>
</a>
</td>
<td>
<code>Secrets<wbr>Provider<wbr>Config</code>
</td>
<td>
</td>
<td>Configuration specific to the secret provider</td>
</tr>
<tr>
<td>
<code>secrets.<wbr>*.name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the secret, used for lookup</td>
</tr>
<tr>
<td>
<code>secrets.<wbr>*.provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the secret provider</td>
</tr>
<tr>
<td>
<code>tracing.<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether or not tracing should be enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#security"><code>security</code></a>
- <a href="io.helidon.webserver.security.SecurityFeature.md#security"><code>server.<wbr>features.<wbr>security.<wbr>security</code></a>

---

See the [manifest](manifest.md) for all available types.
