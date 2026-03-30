# io.helidon.messaging.connectors.jms.AcknowledgeMode

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `AUTO_ACKNOWLEDGE` | Acknowledges automatically after message reception over JMS api |
| `CLIENT_ACKNOWLEDGE` | Message is acknowledged when `org.eclipse.microprofile.reactive.messaging.Message#ack` is invoked either manually or by `org.eclipse.microprofile.reactive.messaging.Acknowledgment` policy |
| `DUPS_OK_ACKNOWLEDGE` | Messages are acknowledged lazily which can result in duplicate messages being delivered |

See the [manifest](../config/manifest.md) for all available types.
