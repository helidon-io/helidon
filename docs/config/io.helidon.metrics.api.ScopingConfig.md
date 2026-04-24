# io.helidon.metrics.api.ScopingConfig

## Description

<code>N/A</code>

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
<code>default</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="application">application</code>
</td>
<td>Default scope value to associate with meters that are registered without an explicit setting; no setting means meters are assigned scope <code>io.helidon.metrics.api.Meter.Scope#DEFAULT</code></td>
</tr>
<tr>
<td>
<a id="scopes"></a>
<a href="io.helidon.metrics.api.ScopeConfig.md">
<code>scopes</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, ScopeConfig&gt;">Map&lt;String, ScopeConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Settings for individual scopes</td>
</tr>
<tr>
<td>
<code>tag-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">scope</code>
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
