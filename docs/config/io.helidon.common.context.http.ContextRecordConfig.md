# io.helidon.common.context.http.ContextRecordConfig

## Description

Configuration of a single propagation record, a mapping of a header name to its context classifier, with optional default value(s), and definition whether it is a single value, or an array

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>default-value</code></td>
<td><code>String</code></td>
<td>Default value to send if not configured in context</td>
</tr>
<tr>
<td><code>array</code></td>
<td><code>Boolean</code></td>
<td>Whether to treat the option as an array of strings</td>
</tr>
<tr>
<td><code>default-values</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>Default values to send if not configured in context</td>
</tr>
<tr>
<td><code>classifier</code></td>
<td><code>String</code></td>
<td>String classifier of the value that will be used with &lt;code&gt;io.helidon.common.context.Context#get(Object, Class)&lt;/code&gt;</td>
</tr>
<tr>
<td><code>header</code></td>
<td><code>RecordCustomMethods</code></td>
<td>Name of the header to use when sending the context value over the network</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.context.records`](io.helidon.webserver.context.ContextFeature.md#records)

---

See the [manifest](manifest.md) for all available types.
