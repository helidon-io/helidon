# io.helidon.messaging.connectors.jms.JmsConfigBuilder

## Description

Build Jms specific config.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4169a-acknowledge-mode"></span> [`acknowledge-mode`](../config/io_helidon_messaging_connectors_jms_AcknowledgeMode.md) | `VALUE` | `i.h.m.c.j.AcknowledgeMode` | `AUTO_ACKNOWLEDGE` | JMS acknowledgement mode |
| <span id="a871ed-destination"></span> `destination` | `VALUE` | `String` |   | Queue or topic name |
| <span id="ad3b43-jndi-initial-context-properties"></span> `jndi-initial-context-properties` | `MAP` | `String` |   | Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url |
| <span id="a0518f-jndi-initial-factory"></span> `jndi-initial-factory` | `VALUE` | `String` |   | JNDI initial factory |
| <span id="ac6c48-jndi-jms-factory"></span> `jndi-jms-factory` | `VALUE` | `String` |   | JNDI name of JMS factory |
| <span id="a9ed59-jndi-provider-url"></span> `jndi-provider-url` | `VALUE` | `String` |   | JNDI provider url |
| <span id="a81cf0-message-selector"></span> `message-selector` | `VALUE` | `String` |   | JMS API message selector expression based on a subset of the SQL92 |
| <span id="a7ace3-named-factory"></span> `named-factory` | `VALUE` | `String` |   | To select from manually configured `jakarta.jms.ConnectionFactory ConnectionFactories` over `JmsConnector.JmsConnectorBuilder#connectionFactory(String, jakarta.jms.ConnectionFactory) JmsConnectorBuilder#connectionFactory()` |
| <span id="a2d6e4-password"></span> `password` | `VALUE` | `String` |   | Password used for creating JMS connection |
| <span id="a1b10e-period-executions"></span> `period-executions` | `VALUE` | `Long` | `100` | Period for executing poll cycles in millis |
| <span id="ae0ce4-poll-timeout"></span> `poll-timeout` | `VALUE` | `Long` | `50` | Timeout for polling for next message in every poll cycle in millis |
| <span id="a08c22-queue"></span> `queue` | `VALUE` | `String` |   | Use supplied destination name and `Type#QUEUE QUEUE` as type |
| <span id="a0f3a9-session-group-id"></span> `session-group-id` | `VALUE` | `String` |   | When multiple channels share same session-group-id, they share same JMS session |
| <span id="ab90ce-topic"></span> `topic` | `VALUE` | `String` |   | Use supplied destination name and `Type#TOPIC TOPIC` as type |
| <span id="ac2dc3-transacted"></span> `transacted` | `VALUE` | `Boolean` | `false` | Indicates whether the session will use a local transaction |
| <span id="aef876-type"></span> [`type`](../config/io_helidon_messaging_connectors_jms_Type.md) | `VALUE` | `i.h.m.c.j.Type` | `QUEUE` | Specify if connection is `Type#QUEUE queue` or `Type#TOPIC topic` |
| <span id="af0324-username"></span> `username` | `VALUE` | `String` |   | User name used for creating JMS connection |

See the [manifest](../config/manifest.md) for all available types.
