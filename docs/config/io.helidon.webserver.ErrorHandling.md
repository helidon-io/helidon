# io.helidon.webserver.ErrorHandling

## Description

&lt;code&gt;N/A&lt;/code&gt;

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
<td><code>include-entity</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to include a response entity when mapping a &lt;code&gt;io.helidon.http.RequestException&lt;/code&gt; using a &lt;code&gt;io.helidon.http.DirectHandler&lt;/code&gt;</td>
</tr>
<tr>
<td><code>log-all-messages</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to log all messages in a &lt;code&gt;io.helidon.http.RequestException&lt;/code&gt; or not</td>
</tr>
</tbody>
</table>


## Usages

- [`server.error-handling`](io.helidon.ServerConfig.md#error-handling)
- [`server.sockets.error-handling`](io.helidon.webserver.ListenerConfig.md#error-handling)

---

See the [manifest](manifest.md) for all available types.
