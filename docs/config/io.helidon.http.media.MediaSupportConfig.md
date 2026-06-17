# io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Media<wbr>Support<wbr>Config

## Description

A set of configurable options expected to be used by each media support

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
<code>accepted-<wbr>media-<wbr>types</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Types accepted by this media support</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the support</td>
</tr>
<tr>
<td>
<code>content-<wbr>type</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Content type to use if not configured (in response headers for server, and in request headers for client)</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>gson.<wbr>Gson<wbr>Support](io.helidon.http.media.gson.GsonSupport.md)
- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>jackson.<wbr>Jackson<wbr>Support](io.helidon.http.media.jackson.JacksonSupport.md)
- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>json.<wbr>Json<wbr>Support](io.helidon.http.media.json.JsonSupport.md)
- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>json.<wbr>binding.<wbr>Json<wbr>Binding<wbr>Support](io.helidon.http.media.json.binding.JsonBindingSupport.md)
- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>json.<wbr>smile.<wbr>Smile<wbr>Support](io.helidon.http.media.json.smile.SmileSupport.md)
- [io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>jsonb.<wbr>Jsonb<wbr>Support](io.helidon.http.media.jsonb.JsonbSupport.md)

---

See the [manifest](manifest.md) for all available types.
