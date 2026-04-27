# io.helidon.webclient.api.WebClientCookieManager

## Description

Helidon WebClient cookie manager

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
<code>automatic-store-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether automatic cookie store is enabled or not</td>
</tr>
<tr>
<td>
<code>cookie-policy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CookiePolicy">CookiePolicy</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER">java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER</code>
</td>
<td>Current cookie policy for this client</td>
</tr>
<tr>
<td>
<code>default-cookies</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Map of default cookies to include in all requests if cookies enabled</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
