# io.helidon.dbclient.jdbc.JdbcParametersConfig

## Description

JDBC parameters setter configuration

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
<code>use-<wbr>byte-<wbr>array-<wbr>binding</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Use <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setBinary<wbr>Stream(<wbr>int,<wbr> java.<wbr>io.Input<wbr>Stream,<wbr> int)</code> binding for <code>byte[]</code> values</td>
</tr>
<tr>
<td>
<code>use-<wbr>string-<wbr>binding</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Use <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setCharacter<wbr>Stream(<wbr>int,<wbr> java.<wbr>io.Reader,<wbr> int)</code> binding for <code>String</code> values with length above <code>#string<wbr>Binding<wbr>Size(<wbr>)</code> limit</td>
</tr>
<tr>
<td>
<code>timestamp-<wbr>for-<wbr>local-<wbr>time</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Use <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setTimestamp(<wbr>int,<wbr> java.<wbr>sql.<wbr>Timestamp)</code> to set <code>java.<wbr>time.<wbr>Local<wbr>Time</code> values when <code>true</code> or use <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setTime(<wbr>int,<wbr> java.<wbr>sql.<wbr>Time)</code> when <code>false</code></td>
</tr>
<tr>
<td>
<code>string-<wbr>binding-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1024</code>
</td>
<td><code>String</code> values with length above this limit will be bound using <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setCharacter<wbr>Stream(<wbr>int,<wbr> java.<wbr>io.Reader,<wbr> int)</code> if <code>#use<wbr>String<wbr>Binding(<wbr>)</code> is set to <code>true</code></td>
</tr>
<tr>
<td>
<code>use-<wbr>n-string</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Use SQL <code>NCHAR</code>, <code>NVARCHAR</code> or <code>LONGNVARCHAR</code> value conversion for <code>String</code> values</td>
</tr>
<tr>
<td>
<code>set-<wbr>object-<wbr>for-<wbr>java-<wbr>time</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Set all <code>java.<wbr>time</code> Date/Time values directly using <code>java.<wbr>sql.<wbr>Prepared<wbr>Statement#<wbr>setObject(<wbr>int,<wbr> Object)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
