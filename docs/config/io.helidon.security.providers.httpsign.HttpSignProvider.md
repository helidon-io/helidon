# io.helidon.security.providers.httpsign.HttpSignProvider

## Description

HTTP header signature provider

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
<td><a id="headers"></a><a href="io.helidon.security.providers.httpsign.HttpSignHeader.md"><code>headers</code></a></td>
<td><code>List&lt;HttpSignHeader&gt;</code></td>
<td></td>
<td>Add a header that is validated on inbound requests</td>
</tr>
<tr>
<td><a id="outbound"></a><a href="io.helidon.security.providers.common.OutboundConfig.md"><code>outbound</code></a></td>
<td><code>OutboundConfig</code></td>
<td></td>
<td>Add outbound targets to this builder</td>
</tr>
<tr>
<td><a id="inbound-keys"></a><a href="io.helidon.security.providers.httpsign.InboundClientDefinition.md"><code>inbound.keys</code></a></td>
<td><code>List&lt;InboundClientDefinition&gt;</code></td>
<td></td>
<td>Add inbound configuration</td>
</tr>
<tr>
<td><code>backward-compatible-eol</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Enable support for Helidon versions before 3.0.0 (exclusive)</td>
</tr>
<tr>
<td><code>optional</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Set whether the signature is optional</td>
</tr>
<tr>
<td><code>realm</code></td>
<td><code>String</code></td>
<td><code>helidon</code></td>
<td>Realm to use for challenging inbound requests that do not have &quot;Authorization&quot; header in case header is &lt;code&gt;HttpSignHeader#AUTHORIZATION&lt;/code&gt; and singatures are not optional</td>
</tr>
<tr>
<td><a id="sign-headers"></a><a href="io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig.md"><code>sign-headers</code></a></td>
<td><code>List&lt;HeadersConfig&gt;</code></td>
<td></td>
<td>Override the default inbound required headers (e.g</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
