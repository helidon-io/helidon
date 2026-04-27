# io.helidon.metrics.api.ScopeConfig

## Description

Configuration settings for a scope within the <code>MetricsConfigBlueprint#METRICS_CONFIG_KEY</code> config section

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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the scope is enabled</td>
</tr>
<tr>
<td>
<a id="filter"></a>
<a href="io.helidon.metrics.scoping.scopes.FilterConfig.md">
<code>filter</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for filter</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the scope to which the configuration applies</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.scoping.scopes`](io.helidon.metrics.api.ScopingConfig.md#scopes)
- [`server.features.observe.observers.metrics.scoping.scopes`](io.helidon.metrics.api.ScopingConfig.md#scopes)

---

See the [manifest](manifest.md) for all available types.
