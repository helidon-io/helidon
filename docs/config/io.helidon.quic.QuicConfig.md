# io.<wbr>helidon.<wbr>quic.<wbr>Quic<wbr>Config

## Description

QUIC protocol configuration and durable transport policy

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
<code>ack-<wbr>delay-<wbr>exponent</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Exponent this endpoint uses to encode the ACK delay it reports to its peer</td>
</tr>
<tr>
<td>
<a id="available-versions"></a>
<a href="io.helidon.quic.QuicVersion.md">
<code>available-<wbr>versions</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Quic<wbr>Version&gt;</code>
</td>
<td>
<code>QUIC_<wbr>V2, QUIC_<wbr>V1</code>
</td>
<td>QUIC versions this endpoint may advertise and negotiate</td>
</tr>
<tr>
<td>
<code>max-<wbr>ack-<wbr>ranges-<wbr>per-<wbr>frame</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1024</code>
</td>
<td>Maximum number of packet-number ranges accepted in one peer ACK frame, including the first ACK range; after structural and packet-type validation, a packet containing a structurally valid ACK frame that exceeds this limit is silently discarded without closing the connection, and connection-state validation of the rejected ACK, such as checking for acknowledgments of unsent or skipped packet numbers, is not performed</td>
</tr>
<tr>
<td>
<code>max-<wbr>uni-<wbr>streams</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>100</code>
</td>
<td>Initial unidirectional stream creation limit advertised to the peer</td>
</tr>
<tr>
<td>
<code>max-<wbr>handshake-<wbr>message-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>32768</code>
</td>
<td>Maximum TLS handshake-message size accepted while reassembling QUIC CRYPTO data</td>
</tr>
<tr>
<td>
<code>socket-<wbr>receive-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Socket receive buffer size applied to a newly opened QUIC endpoint channel</td>
</tr>
<tr>
<td>
<code>active-<wbr>connection-<wbr>id-limit</code>
</td>
<td>
<code>Long</code>
</td>
<td>
</td>
<td>Maximum number of active connection IDs this endpoint is willing to retain from its peer</td>
</tr>
<tr>
<td>
<code>max-<wbr>bidi-<wbr>streams</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>100</code>
</td>
<td>Initial bidirectional stream creation limit advertised to the peer</td>
</tr>
<tr>
<td>
<code>max-<wbr>bytes-<wbr>in-flight</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>16777216</code>
</td>
<td>Maximum number of bytes that congestion control may treat as in flight</td>
</tr>
<tr>
<td>
<code>initial-<wbr>max-<wbr>stream-<wbr>data</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>6291456</code>
</td>
<td>Per-stream flow-control limit advertised to the peer in QUIC transport parameters</td>
</tr>
<tr>
<td>
<code>socket-<wbr>send-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Socket send buffer size applied to a newly opened QUIC endpoint channel</td>
</tr>
<tr>
<td>
<a id="congestion-algorithm"></a>
<a href="io.helidon.quic.QuicCongestionAlgorithm.md">
<code>congestion-<wbr>algorithm</code>
</a>
</td>
<td>
<code>Quic<wbr>Congestion<wbr>Algorithm</code>
</td>
<td>
<code>CUBIC</code>
</td>
<td>Congestion-control algorithm used for newly created connections</td>
</tr>
<tr>
<td>
<code>max-<wbr>ack-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Maximum ACK delay this endpoint advertises to its peer</td>
</tr>
<tr>
<td>
<code>initial-<wbr>max-<wbr>data</code>
</td>
<td>
<code>Long</code>
</td>
<td>
<code>15728640</code>
</td>
<td>Connection-level flow-control limit advertised to the peer in QUIC transport parameters</td>
</tr>
<tr>
<td>
<code>idle-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT30S</code>
</td>
<td>Idle timeout advertised for new QUIC connections</td>
</tr>
<tr>
<td>
<code>max-<wbr>udp-<wbr>payload-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>65527</code>
</td>
<td>Maximum UDP payload size the transport should attempt to send or accept</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>webserver.<wbr>quic.<wbr>Quic<wbr>Transport<wbr>Config](io.helidon.webserver.quic.QuicTransportConfig.md)

---

See the [manifest](manifest.md) for all available types.
