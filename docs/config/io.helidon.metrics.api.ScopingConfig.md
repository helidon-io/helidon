# io.helidon.metrics.api.ScopingConfig

## Description

<code>N/<wbr>A</code>

## Configuration options


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
<code>default</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>application</code>
</td>
<td>Default scope value to associate with meters that are registered without an explicit setting; no setting means meters are assigned scope <code>io.<wbr>helidon.<wbr>metrics.<wbr>api.<wbr>Meter.<wbr>Scope#<wbr>DEFAULT</code></td>
</tr>
<tr>
<td>
<a id="scopes"></a>
<a href="io.helidon.metrics.api.ScopeConfig.md">
<code>scopes</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Scope<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Settings for individual scopes</td>
</tr>
<tr>
<td>
<code>tag-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>scope</code>
</td>
<td>Tag name for storing meter scope values in the underlying implementation meter registry</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.scoping`](io.helidon.MetricsConfig.md#scoping)
- [`server.features.observe.observers.metrics.scoping`](io.helidon.webserver.observe.metrics.MetricsObserver.md#scoping)

---

See the [manifest](manifest.md) for all available types.
