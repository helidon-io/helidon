# io.<wbr>helidon.<wbr>Metrics<wbr>Config

## Description

Merged configuration for metrics

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
<code>app-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Value for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<code>app-<wbr>tag-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name for the application tag to be added to each meter ID</td>
</tr>
<tr>
<td>
<a id="auto-http-metrics"></a>
<a href="io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig.md">
<code>auto-<wbr>http-<wbr>metrics</code>
</a>
</td>
<td>
<code>Auto<wbr>Http<wbr>Metrics<wbr>Config</code>
</td>
<td>
</td>
<td>Automatic metrics collection settings</td>
</tr>
<tr>
<td>
<a id="built-in-meter-name-format"></a>
<a href="io.helidon.metrics.api.BuiltInMeterNameFormat.md">
<code>built-<wbr>in-meter-<wbr>name-<wbr>format</code>
</a>
</td>
<td>
<code>Built<wbr>InMeter<wbr>Name<wbr>Format</code>
</td>
<td>
<code>CAMEL</code>
</td>
<td>Output format for built-in meter names</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether metrics functionality is enabled</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>metrics</code>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<a id="key-performance-indicators"></a>
<a href="io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig.md">
<code>key-<wbr>performance-<wbr>indicators</code>
</a>
</td>
<td>
<code>Key<wbr>Performance<wbr>Indicator<wbr>Metrics<wbr>Config</code>
</td>
<td>
</td>
<td>Key performance indicator metrics settings</td>
</tr>
<tr>
<td>
<code>permit-<wbr>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
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
<td>
<code>List&lt;<wbr>Metrics<wbr>Publisher&gt;</code>
</td>
<td>
</td>
<td>Metrics publishers which make the metrics data available to external systems</td>
</tr>
<tr>
<td>
<code>publishers-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>publishers</code></td>
</tr>
<tr>
<td>
<a id="rest-request"></a>
<a href="io.helidon.metrics.RestRequestConfig.md">
<code>rest-<wbr>request</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for rest-request</td>
</tr>
<tr>
<td>
<code>roles</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
<code>observe</code>
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
<td>
<code>Scoping<wbr>Config</code>
</td>
<td>
</td>
<td>Settings related to scoping management</td>
</tr>
<tr>
<td>
<code>tags</code>
</td>
<td>
<code>List&lt;<wbr>Metrics<wbr>Config<wbr>Support&gt;</code>
</td>
<td>
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
<td>
</td>
<td>
</td>
<td>Configuration for timers</td>
</tr>
<tr>
<td>
<a id="virtual-threads"></a>
<a href="io.helidon.metrics.VirtualThreadsConfig.md">
<code>virtual-<wbr>threads</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for virtual-threads</td>
</tr>
<tr>
<td>
<code>warn-<wbr>on-multiple-<wbr>registries</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
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
<code>gc-<wbr>time-<wbr>type</code>
</a>
</td>
<td>
<code>Gc<wbr>Time<wbr>Type</code>
</td>
<td>
<code>COUNTER</code>
</td>
<td>Whether the <code>gc.<wbr>time</code> meter should be registered as a gauge (vs</td>
</tr>
<tr>
<td>
<code>rest-<wbr>request-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Whether automatic REST request metrics should be measured (as indicated by the deprecated config key <code>rest-<wbr>request-<wbr>enabled</code>, the config key using a hyphen instead of a dot separator)</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.<wbr>helidon.<wbr>metrics.<wbr>api.<wbr>Metrics<wbr>Config](io.helidon.metrics.api.MetricsConfig.md)
- [io.<wbr>helidon.<wbr>webserver.<wbr>observe.<wbr>metrics.<wbr>Metrics<wbr>Observer](io.helidon.webserver.observe.metrics.MetricsObserver.md)

## Usages

- <a href="config_reference.md#metrics"><code>metrics</code></a>

---

See the [manifest](manifest.md) for all available types.
