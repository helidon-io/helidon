# io.helidon.metrics.api.ScopingConfig

## Description

&lt;code&gt;N/A&lt;/code&gt;

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
<td><code>default</code></td>
<td><code>String</code></td>
<td><code>application</code></td>
<td>Default scope value to associate with meters that are registered without an explicit setting; no setting means meters are assigned scope &lt;code&gt;io.helidon.metrics.api.Meter.Scope#DEFAULT&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="scopes"></a><a href="io.helidon.metrics.api.ScopeConfig.md"><code>scopes</code></a></td>
<td><code>Map&lt;String, ScopeConfig&gt;</code></td>
<td></td>
<td>Settings for individual scopes</td>
</tr>
<tr>
<td><code>tag-name</code></td>
<td><code>String</code></td>
<td><code>scope</code></td>
<td>Tag name for storing meter scope values in the underlying implementation meter registry</td>
</tr>
</tbody>
</table>


## Usages

- [`metrics.scoping`](io.helidon.MetricsConfig.md#scoping)
- [`server.features.observe.observers.metrics.scoping`](io.helidon.webserver.observe.metrics.MetricsObserver.md#scoping)

---

See the [manifest](manifest.md) for all available types.
