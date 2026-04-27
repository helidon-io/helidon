# io.helidon.webserver.observe.ObserveFeature

## Description

Configuration for observability feature itself

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
<code class="cm-truncate-value">/observe</code>
</td>
<td>Root endpoint to use for observe providers</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">80.0</code>
</td>
<td>Change the weight of this feature</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sockets the observability endpoint should be exposed on</td>
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
<td>Whether the observe support is enabled</td>
</tr>
<tr>
<td>
<a id="observers"></a>
<a href="io.helidon.webserver.observe.spi.Observer.md">
<code>observers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Observer&gt;">List&lt;Observer&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Observers to use with this observe features</td>
</tr>
<tr>
<td>
<code>observers-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>observers</code></td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="cors"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cors</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CrossOriginConfig">CrossOriginConfig</code>
</td>
<td>Cors support inherited by each observe provider, unless explicitly configured</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.observe`](io.helidon.webserver.spi.ServerFeature.md#observe)

---

See the [manifest](manifest.md) for all available types.
