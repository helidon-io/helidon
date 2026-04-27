# io.helidon.webserver.observe.config.ConfigObserver

## Description

Configuration of Config Observer

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
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">config</code>
</td>
<td>Endpoint this observer is available on</td>
</tr>
<tr>
<td>
<code>permit-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Permit all access, even when not authorized</td>
</tr>
<tr>
<td>
<code>secrets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title=".*password, .*passphrase, .*secret">.*password, .*passphrase, .*secret</code>
</td>
<td>Secret patterns (regular expressions) to exclude from output</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.config`](io.helidon.webserver.observe.spi.Observer.md#config)

---

See the [manifest](manifest.md) for all available types.
