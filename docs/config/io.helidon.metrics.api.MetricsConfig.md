# io.helidon.metrics.api.MetricsConfig

## Description

Configuration settings for metrics

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
<td><code>publishers-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;publishers&lt;/code&gt;</td>
</tr>
<tr>
<td><code>rest-request.enabled</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether automatic REST request metrics should be measured</td>
</tr>
<tr>
<td><code>warn-on-multiple-registries</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to log warnings when multiple registries are created</td>
</tr>
<tr>
<td><code>roles</code></td>
<td><code>List&lt;String&gt;</code></td>
<td><code>observe</code></td>
<td>Hints for role names the user is expected to be in</td>
</tr>
<tr>
<td><code>virtual-threads.pinned.threshold</code></td>
<td><code>Duration</code></td>
<td><code>PT0.020S</code></td>
<td>Threshold for sampling pinned virtual threads to include in the pinned threads meter</td>
</tr>
<tr>
<td><a id="built-in-meter-name-format"></a><a href="io.helidon.metrics.api.BuiltInMeterNameFormat.md"><code>built-in-meter-name-format</code></a></td>
<td><code>BuiltInMeterNameFormat</code></td>
<td><code>CAMEL</code></td>
<td>Output format for built-in meter names</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether metrics functionality is enabled</td>
</tr>
<tr>
<td><code>app-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Value for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td><code>tags</code></td>
<td><code>List&lt;MetricsConfigSupport&gt;</code></td>
<td></td>
<td>Global tags</td>
</tr>
<tr>
<td><code>app-tag-name</code></td>
<td><code>String</code></td>
<td></td>
<td>Name for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td><code>virtual-threads.enabled</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether Helidon should expose meters related to virtual threads</td>
</tr>
<tr>
<td><a id="publishers"></a><a href="io.helidon.metrics.api.MetricsPublisher.md"><code>publishers</code></a></td>
<td><code>List&lt;MetricsPublisher&gt;</code></td>
<td></td>
<td>Metrics publishers which make the metrics data available to external systems</td>
</tr>
<tr>
<td><a id="key-performance-indicators"></a><a href="io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig.md"><code>key-performance-indicators</code></a></td>
<td><code>KeyPerformanceIndicatorMetricsConfig</code></td>
<td></td>
<td>Key performance indicator metrics settings</td>
</tr>
<tr>
<td><code>permit-all</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to allow anybody to access the endpoint</td>
</tr>
<tr>
<td><a id="scoping"></a><a href="io.helidon.metrics.api.ScopingConfig.md"><code>scoping</code></a></td>
<td><code>ScopingConfig</code></td>
<td></td>
<td>Settings related to scoping management</td>
</tr>
<tr>
<td><a id="timers-json-units-default"></a><a href="java.util.concurrent.TimeUnit.md"><code>timers.json-units-default</code></a></td>
<td><code>TimeUnit</code></td>
<td></td>
<td>Default units for timer output in JSON if not specified on a given timer</td>
</tr>
</tbody>
</table>

### Deprecated Options

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>rest-request-enabled</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Whether automatic REST request metrics should be measured (as indicated by the deprecated config key &lt;code&gt;rest-request-enabled&lt;/code&gt;, the config key using a hyphen instead of a dot separator)</td>
</tr>
<tr>
<td><a id="gc-time-type"></a><a href="io.helidon.metrics.api.GcTimeType.md"><code>gc-time-type</code></a></td>
<td><code>GcTimeType</code></td>
<td><code>COUNTER</code></td>
<td>Whether the &lt;code&gt;gc.time&lt;/code&gt; meter should be registered as a gauge (vs</td>
</tr>
</tbody>
</table>

## Dependent Types

- [io.helidon.webserver.observe.metrics.MetricsObserver](io.helidon.webserver.observe.metrics.MetricsObserver.md)

---

See the [manifest](manifest.md) for all available types.
