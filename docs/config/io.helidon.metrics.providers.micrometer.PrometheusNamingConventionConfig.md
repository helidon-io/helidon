# io.<wbr>helidon.<wbr>metrics.<wbr>providers.<wbr>micrometer.<wbr>Prometheus<wbr>Naming<wbr>Convention<wbr>Config

## Description

Settings controlling Prometheus naming conventions

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>non-<wbr>letter-<wbr>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>Prefix to add to metric names and tag keys which do not begin with a letter; configuring this setting enables legacy simpleclient-compatible normalization, with <code>m_</code> reproducing the naming from earlier Helidon releases, and preserves user-supplied reserved suffixes such as <code>_total</code>, <code>_created</code>, <code>_bucket</code>, and <code>_info</code>, whereas leaving it unset uses the new Prometheus client's normalization</td>
</tr>
<tr>
<td>
<code>timer-<wbr>suffix</code>
</td>
<td>
<code>String</code>
</td>
<td>Suffix which identifies a timer name before Prometheus adds <code>_seconds</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#naming-convention"><code>metrics.<wbr>publishers.<wbr>prometheus.<wbr>naming-<wbr>convention</code></a>
- <a href="io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#naming-convention"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>publishers.<wbr>prometheus.<wbr>naming-<wbr>convention</code></a>

---

See the [manifest](manifest.md) for all available types.
