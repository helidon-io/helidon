# io.helidon.metrics.api.ScopeConfig

## Description

Configuration settings for a scope within the &lt;code&gt;MetricsConfigBlueprint#METRICS_CONFIG_KEY&lt;/code&gt; config section

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
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the scope is enabled</td>
</tr>
<tr>
<td><a id="filter"></a><a href="io.helidon.metrics.scoping.scopes.FilterConfig.md"><code>filter</code></a></td>
<td></td>
<td></td>
<td>Configuration for filter</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td></td>
<td>Name of the scope to which the configuration applies</td>
</tr>
</tbody>
</table>


## Usages

- [`metrics.scoping.scopes`](io.helidon.metrics.api.ScopingConfig.md#scopes)
- [`server.features.observe.observers.metrics.scoping.scopes`](io.helidon.metrics.api.ScopingConfig.md#scopes)

---

See the [manifest](manifest.md) for all available types.
