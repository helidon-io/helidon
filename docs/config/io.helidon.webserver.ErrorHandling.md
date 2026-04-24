# io.helidon.webserver.ErrorHandling

## Description

<code>N/A</code>

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
<code>include-entity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to include a response entity when mapping a <code>io.helidon.http.RequestException</code> using a <code>io.helidon.http.DirectHandler</code></td>
</tr>
<tr>
<td>
<code>log-all-messages</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to log all messages in a <code>io.helidon.http.RequestException</code> or not</td>
</tr>
</tbody>
</table>



## Usages

- [`server.error-handling`](io.helidon.webserver.WebServer.md#error-handling)
- [`server.sockets.error-handling`](io.helidon.webserver.ListenerConfig.md#error-handling)

---

See the [manifest](manifest.md) for all available types.
