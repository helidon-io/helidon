# io.helidon.dbclient.jdbc.JdbcParametersConfig

## Description

JDBC parameters setter configuration.

## Usages

## Configuration options

<table class="tableblock frame-all grid-all stretch">
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">Key</th>
<th class="tableblock halign-left valign-top">Kind</th>
<th class="tableblock halign-left valign-top">Type</th>
<th class="tableblock halign-left valign-top">Default Value</th>
<th class="tableblock halign-left valign-top">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0fbf6-set-object-for-java-time"></span> <code>set-object-for-java-time</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Set all <code>java.time</code> Date/Time values directly using <code>java.sql.PreparedStatement#setObject(int, Object)</code></p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a02f93-string-binding-size"></span> <code>string-binding-size</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Integer</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>1024</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code> values with length above this limit will be bound using <code>java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)</code> if <code>#useStringBinding()</code> is set to <code>true</code></p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a4ce4c-timestamp-for-local-time"></span> <code>timestamp-for-local-time</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Use <code>java.sql.PreparedStatement#setTimestamp(int, java.sql.Timestamp)</code> to set <code>java.time.LocalTime</code> values when <code>true</code> or use <code>java.sql.PreparedStatement#setTime(int, java.sql.Time)</code> when <code>false</code></p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a5dc6e-use-byte-array-binding"></span> <code>use-byte-array-binding</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top">Use <code>java.sql.PreparedStatement#setBinaryStream(int, java.io.InputStream, int)</code> binding for
byte[&lt;/code&gt; values]</td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="af5667-use-n-string"></span> <code>use-n-string</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Use SQL <code>NCHAR</code>, <code>NVARCHAR</code> or <code>LONGNVARCHAR</code> value conversion for <code>String</code> values</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a47ec2-use-string-binding"></span> <code>use-string-binding</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Use <code>java.sql.PreparedStatement#setCharacterStream(int, java.io.Reader, int)</code> binding for <code>String</code> values with length above <code>#stringBindingSize()</code> limit</p></td>
</tr>
</tbody>
</table>

------------------------------------------------------------------------

See the [manifest](../config/manifest.md) for all available types.
