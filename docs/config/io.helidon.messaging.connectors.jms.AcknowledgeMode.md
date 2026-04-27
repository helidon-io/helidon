# io.helidon.messaging.connectors.jms.AcknowledgeMode

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>AUTO_ACKNOWLEDGE</code></td>
<td>Acknowledges automatically after message reception over JMS api</td>
</tr>
<tr>
<td><code>CLIENT_ACKNOWLEDGE</code></td>
<td>Message is acknowledged when <code>org.eclipse.microprofile.reactive.messaging.Message#ack</code> is invoked either manually or by <code>org.eclipse.microprofile.reactive.messaging.Acknowledgment</code> policy</td>
</tr>
<tr>
<td><code>DUPS_OK_ACKNOWLEDGE</code></td>
<td>Messages are acknowledged lazily which can result in duplicate messages being delivered</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
