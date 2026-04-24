# io.helidon.webserver.observe.info.InfoObserver

## Description

Info Observer configuration

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
<code class="cm-truncate-value">info</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>values</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Values to be exposed using this observability endpoint</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.info`](io.helidon.webserver.observe.spi.Observer.md#info)

---

See the [manifest](manifest.md) for all available types.
