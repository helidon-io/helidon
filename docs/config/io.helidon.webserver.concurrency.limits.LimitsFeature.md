# io.helidon.webserver.concurrency.limits.LimitsFeature

## Description

Server feature that adds limits as filters

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
<code>concurrency-limit-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>concurrency-limit</code></td>
</tr>
<tr>
<td>
<a id="concurrency-limit"></a>
<a href="io.helidon.common.concurrency.limits.Limit.md">
<code>concurrency-limit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Limit</code>
</td>
<td class="cm-default-cell">
</td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">2000.0</code>
</td>
<td>Weight of the context feature</td>
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
<td>List of sockets to register this feature on</td>
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
<td>Whether this feature is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.limits`](io.helidon.webserver.spi.ServerFeature.md#limits)

---

See the [manifest](manifest.md) for all available types.
