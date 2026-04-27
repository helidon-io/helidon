# io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig

## Description

Configuration of headers to be signed

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>always</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Headers that must be signed (and signature validation or creation should fail if not signed or present)</td>
</tr>
<tr>
<td>
<code>if-present</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Headers that must be signed if present in request</td>
</tr>
<tr>
<td>
<code>method</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>HTTP method this header configuration is bound to. If not present, it is considered default header configuration</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
