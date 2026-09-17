# io.<wbr>helidon.<wbr>common.<wbr>context.<wbr>http.<wbr>Context<wbr>Record<wbr>Config

## Description

Configuration of a single propagation record, a mapping of a header name to its context classifier, with optional default value(s), and definition whether it is a single value, or an array

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
<code>default-<wbr>value</code>
</td>
<td>
<code>String</code>
</td>
<td>Default value to send if not configured in context</td>
</tr>
<tr>
<td>
<code>array</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether to treat the option as an array of strings</td>
</tr>
<tr>
<td>
<code>default-<wbr>values</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Default values to send if not configured in context</td>
</tr>
<tr>
<td>
<code>classifier</code>
</td>
<td>
<code>String</code>
</td>
<td>String classifier of the value that will be used with <code>io.<wbr>helidon.<wbr>common.<wbr>context.<wbr>Context#<wbr>get(<wbr>Object,<wbr> Class)</code></td>
</tr>
<tr>
<td>
<code>header</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the header to use when sending the context value over the network</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.context.ContextFeature.md#records"><code>server.<wbr>features.<wbr>context.<wbr>records</code></a>

---

See the [manifest](manifest.md) for all available types.
