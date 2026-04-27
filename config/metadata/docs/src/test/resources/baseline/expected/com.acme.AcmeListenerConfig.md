# com.acme.AcmeListenerConfig

## Description

ACME Listener configuration

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
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Listen port</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0.0.0.0</code>
</td>
<td>Listen address</td>
</tr>
</tbody>
</table>



## Dependent Types

- [com.acme.AcmeServerConfig](com.acme.AcmeServerConfig.md)

## Usages

- [`server.sockets`](com.acme.AcmeServerConfig.md#sockets)

---

See the [manifest](manifest.md) for all available types.
