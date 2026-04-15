# io.helidon.webclient.api.WebClientCookieManager

## Description

Helidon WebClient cookie manager

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
<td><code>automatic-store-enabled</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether automatic cookie store is enabled or not</td>
</tr>
<tr>
<td><code>cookie-policy</code></td>
<td><code>CookiePolicy</code></td>
<td><code>java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER</code></td>
<td>Current cookie policy for this client</td>
</tr>
<tr>
<td><code>default-cookies</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Map of default cookies to include in all requests if cookies enabled</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
