# io.helidon.integrations.eureka.EurekaRegistrationServerFeature

## Description

A <code>Prototype.Api prototype</code> for <code>EurekaRegistrationServerFeature</code> <code>io.helidon.builder.api.RuntimeType.Api runtime type</code> instances

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
<a id="instance"></a>
<a href="io.helidon.integrations.eureka.InstanceInfoConfig.md">
<code>instance</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="InstanceInfoConfig">InstanceInfoConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>An <code>InstanceInfoConfig</code> describing the service instance to be registered</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100.0</code>
</td>
<td>The (zero or positive) <code>io.helidon.common.Weighted weight</code> of this instance</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether this feature will be enabled</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.eureka`](io.helidon.webserver.spi.ServerFeature.md#eureka)

---

See the [manifest](manifest.md) for all available types.
