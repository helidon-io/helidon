# io.<wbr>helidon.<wbr>messaging.<wbr>connectors.<wbr>jms.<wbr>Acknowledge<wbr>Mode

## Description

This type is an enumeration.

## Allowed Values

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>AUTO_<wbr>ACKNOWLEDGE</code></td>
<td>Acknowledges automatically after message reception over JMS api</td>
</tr>
<tr>
<td><code>CLIENT_<wbr>ACKNOWLEDGE</code></td>
<td>Message is acknowledged when <code>org.<wbr>eclipse.<wbr>microprofile.<wbr>reactive.<wbr>messaging.<wbr>Message#<wbr>ack</code> is invoked either manually or by <code>org.<wbr>eclipse.<wbr>microprofile.<wbr>reactive.<wbr>messaging.<wbr>Acknowledgment</code> policy</td>
</tr>
<tr>
<td><code>DUPS_<wbr>OK_ACKNOWLEDGE</code></td>
<td>Messages are acknowledged lazily which can result in duplicate messages being delivered</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
