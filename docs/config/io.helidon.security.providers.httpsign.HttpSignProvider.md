# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>httpsign.<wbr>Http<wbr>Sign<wbr>Provider

## Description

HTTP header signature provider

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
<a id="headers"></a>
<a href="io.helidon.security.providers.httpsign.HttpSignHeader.md">
<code>headers</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Http<wbr>Sign<wbr>Header&gt;</code>
</td>
<td>
</td>
<td>Add a header that is validated on inbound requests</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundConfig.md">
<code>outbound</code>
</a>
</td>
<td>
<code>Outbound<wbr>Config</code>
</td>
<td>
</td>
<td>Add outbound targets to this builder</td>
</tr>
<tr>
<td>
<a id="inbound-keys"></a>
<a href="io.helidon.security.providers.httpsign.InboundClientDefinition.md">
<code>inbound.<wbr>keys</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Inbound<wbr>Client<wbr>Definition&gt;</code>
</td>
<td>
</td>
<td>Add inbound configuration</td>
</tr>
<tr>
<td>
<code>backward-<wbr>compatible-<wbr>eol</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Enable support for Helidon versions before 3.0.0 (exclusive)</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Set whether the signature is optional</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>helidon</code>
</td>
<td>Realm to use for challenging inbound requests that do not have "Authorization" header in case header is <code>Http<wbr>Sign<wbr>Header#<wbr>AUTHORIZATION</code> and singatures are not optional</td>
</tr>
<tr>
<td>
<a id="sign-headers"></a>
<a href="io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig.md">
<code>sign-<wbr>headers</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Headers<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Override the default inbound required headers (e.g</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
