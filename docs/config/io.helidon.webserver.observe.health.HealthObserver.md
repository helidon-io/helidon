# io.helidon.webserver.observe.health.HealthObserver

## Description

Configuration of Health observer

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
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">health</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>details</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether details should be printed</td>
</tr>
<tr>
<td>
<code>exclude</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Health check names to exclude in computing the overall health of the server</td>
</tr>
<tr>
<td>
<code>use-system-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to use services discovered by <code>java.util.ServiceLoader</code></td>
</tr>
</tbody>
</table>



## Usages

- [`health`](config_reference.md#health)
- [`server.features.observe.observers.health`](io.helidon.webserver.observe.spi.Observer.md#health)

---

See the [manifest](manifest.md) for all available types.
