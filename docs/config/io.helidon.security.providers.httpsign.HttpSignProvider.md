# io.helidon.security.providers.httpsign.HttpSignProvider

## Description

HTTP header signature provider

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;HttpSignHeader&gt;">List&lt;HttpSignHeader&gt;</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OutboundConfig">OutboundConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add outbound targets to this builder</td>
</tr>
<tr>
<td>
<a id="inbound-keys"></a>
<a href="io.helidon.security.providers.httpsign.InboundClientDefinition.md">
<code>inbound.keys</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;InboundClientDefinition&gt;">List&lt;InboundClientDefinition&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Add inbound configuration</td>
</tr>
<tr>
<td>
<code>backward-compatible-eol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Enable support for Helidon versions before 3.0.0 (exclusive)</td>
</tr>
<tr>
<td>
<code>optional</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Set whether the signature is optional</td>
</tr>
<tr>
<td>
<code>realm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon</code>
</td>
<td>Realm to use for challenging inbound requests that do not have "Authorization" header in case header is <code>HttpSignHeader#AUTHORIZATION</code> and singatures are not optional</td>
</tr>
<tr>
<td>
<a id="sign-headers"></a>
<a href="io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig.md">
<code>sign-headers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;HeadersConfig&gt;">List&lt;HeadersConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Override the default inbound required headers (e.g</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
