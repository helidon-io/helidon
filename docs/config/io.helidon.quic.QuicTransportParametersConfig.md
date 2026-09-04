# io.<wbr>helidon.<wbr>quic.<wbr>Quic<wbr>Transport<wbr>Parameters<wbr>Config

## Description

Additional QUIC transport parameters for client and server endpoints; active connection migration is not supported, so endpoints always advertise the <code>disable_<wbr>active_<wbr>migration</code> transport parameter

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
<code>ack-<wbr>delay-<wbr>exponent</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Exponent this endpoint uses to encode the ACK delay it reports to its peer</td>
</tr>
<tr>
<td>
<code>max-<wbr>ack-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>Maximum ACK delay this endpoint advertises to its peer</td>
</tr>
<tr>
<td>
<code>active-<wbr>connection-<wbr>id-limit</code>
</td>
<td>
<code>Long</code>
</td>
<td>Maximum number of active connection IDs this endpoint is willing to retain from its peer</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>quic.<wbr>Quic<wbr>Config](io.helidon.quic.QuicConfig.md)

---

See the [manifest](manifest.md) for all available types.
