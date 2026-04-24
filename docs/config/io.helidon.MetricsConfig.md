# io.helidon.MetricsConfig

## Description

Merged configuration for metrics

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
<code>app-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Value for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<code>app-tag-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<a id="auto-http-metrics"></a>
<a href="io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md">
<code>auto-http-metrics</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AutoHttpMetricsConfig">AutoHttpMetricsConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Automatic metrics collection settings</td>
</tr>
<tr>
<td>
<a id="built-in-meter-name-format"></a>
<a href="io.helidon.metrics.api.BuiltInMeterNameFormat.md">
<code>built-in-meter-name-format</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="BuiltInMeterNameFormat">BuiltInMeterNameFormat</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CAMEL</code>
</td>
<td>Output format for built-in meter names</td>
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
<td>Whether metrics functionality is enabled</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">metrics</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<a id="key-performance-indicators"></a>
<a href="io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig.md">
<code>key-performance-indicators</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="KeyPerformanceIndicatorMetricsConfig">KeyPerformanceIndicatorMetricsConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Key performance indicator metrics settings</td>
</tr>
<tr>
<td>
<code>permit-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td>
<a id="publishers"></a>
<a href="io.helidon.metrics.api.MetricsPublisher.md">
<code>publishers</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;MetricsPublisher&gt;">List&lt;MetricsPublisher&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Metrics publishers which make the metrics data available to external systems</td>
</tr>
<tr>
<td>
<code>publishers-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>publishers</code></td>
</tr>
<tr>
<td>
<a id="rest-request"></a>
<a href="io.helidon.metrics.RestRequestConfig.md">
<code>rest-request</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for rest-request</td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">observe</code>
</td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td>
<a id="scoping"></a>
<a href="io.helidon.metrics.api.ScopingConfig.md">
<code>scoping</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ScopingConfig">ScopingConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Settings related to scoping management</td>
</tr>
<tr>
<td>
<code>tags</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;MetricsConfigSupport&gt;">List&lt;MetricsConfigSupport&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Global tags</td>
</tr>
<tr>
<td>
<a id="timers"></a>
<a href="io.helidon.metrics.TimersConfig.md">
<code>timers</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for timers</td>
</tr>
<tr>
<td>
<a id="virtual-threads"></a>
<a href="io.helidon.metrics.VirtualThreadsConfig.md">
<code>virtual-threads</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for virtual-threads</td>
</tr>
<tr>
<td>
<code>warn-on-multiple-registries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to log warnings when multiple registries are created</td>
</tr>
</tbody>
</table>


### Deprecated Options


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
<a id="gc-time-type"></a>
<a href="io.helidon.metrics.api.GcTimeType.md">
<code>gc-time-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">GcTimeType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">COUNTER</code>
</td>
<td>Whether the <code>gc.time</code> meter should be registered as a gauge (vs</td>
</tr>
<tr>
<td>
<code>rest-request-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Whether automatic REST request metrics should be measured (as indicated by the deprecated config key <code>rest-request-enabled</code>, the config key using a hyphen instead of a dot separator)</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.helidon.metrics.api.MetricsConfig](io.helidon.metrics.api.MetricsConfig.md)
- [io.helidon.webserver.observe.metrics.MetricsObserver](io.helidon.webserver.observe.metrics.MetricsObserver.md)

## Usages

- [`metrics`](config_reference.md#metrics)

---

See the [manifest](manifest.md) for all available types.
