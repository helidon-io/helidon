# io.helidon.security.SecurityTime

## Description

Time used in security, configurable

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
<td><code>month</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>shift-by-seconds</code></td>
<td><code>Long</code></td>
<td><code>0</code></td>
<td>Configure a time-shift in seconds, to move the current time to past or future</td>
</tr>
<tr>
<td><code>year</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>millisecond</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>hour-of-day</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>day-of-month</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>time-zone</code></td>
<td><code>ZoneId</code></td>
<td></td>
<td>Override current time zone</td>
</tr>
<tr>
<td><code>minute</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
<tr>
<td><code>second</code></td>
<td><code>Long</code></td>
<td></td>
<td>Set an explicit value for one of the time fields (such as &lt;code&gt;ChronoField#YEAR&lt;/code&gt;)</td>
</tr>
</tbody>
</table>


## Usages

- [`security.environment.server-time`](io.helidon.security.EnvironmentConfig.md#server-time)
- [`server.features.security.security.environment.server-time`](io.helidon.server.features.security.security.EnvironmentConfig.md#server-time)

---

See the [manifest](manifest.md) for all available types.
