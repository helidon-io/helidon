# io.helidon.integrations.eureka.LeaseInfoConfig

## Description

A <code>Prototype.Api prototype</code> describing initial Eureka Server service instance registration lease details

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
<code>duration</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">90</code>
</td>
<td>The lease duration in seconds; the default value is strongly recommended</td>
</tr>
<tr>
<td>
<code>renewalInterval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">30</code>
</td>
<td>The lease renewal interval in seconds; the default value is strongly recommended</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.eureka.instance.lease`](io.helidon.integrations.eureka.InstanceInfoConfig.md#lease)

---

See the [manifest](manifest.md) for all available types.
