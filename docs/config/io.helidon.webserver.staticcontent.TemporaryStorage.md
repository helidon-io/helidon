# io.<wbr>helidon.<wbr>webserver.<wbr>staticcontent.<wbr>Temporary<wbr>Storage

## Description

Configuration of temporary storage for classpath based handlers

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
<code>file-<wbr>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>helidon-<wbr>ws</code>
</td>
<td>Prefix of the files in temporary storage</td>
</tr>
<tr>
<td>
<code>delete-<wbr>on-exit</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether temporary files should be deleted on JVM exit</td>
</tr>
<tr>
<td>
<code>file-<wbr>suffix</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>.je</code>
</td>
<td>Suffix of the files in temporary storage</td>
</tr>
<tr>
<td>
<code>directory</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>Location of the temporary storage, defaults to temporary storage configured for the JVM</td>
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
<td>Whether the temporary storage is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md#temporary-storage"><code>server.<wbr>features.<wbr>static-<wbr>content.<wbr>classpath.<wbr>temporary-<wbr>storage</code></a>
- <a href="io.helidon.webserver.staticcontent.StaticContentFeature.md#temporary-storage"><code>server.<wbr>features.<wbr>static-<wbr>content.<wbr>temporary-<wbr>storage</code></a>

---

See the [manifest](manifest.md) for all available types.
