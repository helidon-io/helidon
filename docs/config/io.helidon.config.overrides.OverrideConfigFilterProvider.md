# io.<wbr>helidon.<wbr>config.<wbr>overrides.<wbr>Override<wbr>Config<wbr>Filter<wbr>Provider

## Description

Configuration of an override filter provider

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
<code>sources</code>
</td>
<td>
<code>List&lt;<wbr>Config&gt;</code>
</td>
<td>Standard Config source descriptors for files containing override expressions</td>
</tr>
<tr>
<td>
<a id="patterns"></a>
<a href="io.helidon.config.overrides.OverridePatternConfig.md">
<code>patterns</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Override<wbr>Pattern<wbr>Config&gt;</code>
</td>
<td>Ordered regular expression rules</td>
</tr>
<tr>
<td>
<code>expressions</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Explicit config override settings, using expressions with <code>*</code> to match one or more word characters</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#overrides"><code>overrides</code></a>

---

See the [manifest](manifest.md) for all available types.
