# io.<wbr>helidon.<wbr>webserver.<wbr>UdsTransport<wbr>Config

## Description

Configuration of the built-in Unix domain socket listener transport binding

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
<code>socket</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>
</td>
<td>Unix domain socket address to bind</td>
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
<td>Whether this binding is enabled</td>
</tr>
<tr>
<td>
<code>required</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether this binding is required to become active</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.spi.TransportBindingFactory.md#uds"><code>server.<wbr>bindings.<wbr>uds</code></a>
- <a href="io.helidon.webserver.spi.TransportBindingFactory.md#uds"><code>server.<wbr>sockets.<wbr>bindings.<wbr>uds</code></a>

---

See the [manifest](manifest.md) for all available types.
