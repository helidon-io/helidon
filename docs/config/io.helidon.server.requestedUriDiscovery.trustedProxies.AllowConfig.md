# io.helidon.server.requestedUriDiscovery.trustedProxies.AllowConfig

## Description

Configuration for server.requested-uri-discovery.trusted-proxies.allow

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
<code>all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Allows all strings to match (subject to "deny" conditions)</td>
</tr>
<tr>
<td>
<code>exact</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Exact strings to allow</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Pattern&gt;">List&lt;Pattern&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>Pattern</code>s specifying strings to allow</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Prefixes specifying strings to allow</td>
</tr>
<tr>
<td>
<code>suffix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Suffixes specifying strings to allow</td>
</tr>
</tbody>
</table>



## Usages

- [`server.requested-uri-discovery.trusted-proxies.allow`](io.helidon.common.configurable.AllowList.md#allow)

---

See the [manifest](manifest.md) for all available types.
