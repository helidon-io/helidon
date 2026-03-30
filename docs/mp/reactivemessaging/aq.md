# Oracle AQ Connector

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Configuration](#configuration)
- [Usage](#usage)

## Overview

Connecting streams to Oracle AQ with Reactive Messaging couldn’t be easier. This connector extends Helidon’s JMS connector with Oracle’s AQ-specific API.

## Maven Coordinates

To enable AQ Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.messaging.aq</groupId>
    <artifactId>helidon-messaging-aq</artifactId>
</dependency>
```

## Configuration

Connector name: `helidon-aq`

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4e0a1-acknowledge-mode"></span> [`acknowledge-mode`](../../config/io_helidon_messaging_connectors_jms_AcknowledgeMode.md) | `VALUE` | `i.h.m.c.j.AcknowledgeMode` | `AUTO_ACKNOWLEDGE` | JMS acknowledgement mode |
| <span id="aa8394-client-id"></span> `client-id` | `VALUE` | `String` |   | Client identifier for JMS connection |
| <span id="ab7c5d-data-source"></span> `data-source` | `VALUE` | `String` |   | Mapping to `javax.sql.DataSource DataSource` supplied with `io.helidon.messaging.connectors.aq.AqConnector.AqConnectorBuilder#dataSource(String, javax.sql.DataSource) AqConnectorBuilder.dataSource()` |
| <span id="a750e3-destination"></span> `destination` | `VALUE` | `String` |   | Queue or topic name |
| <span id="ad5200-durable"></span> `durable` | `VALUE` | `Boolean` | `false` | Indicates whether the consumer should be created as durable (only relevant for topic destinations) |
| <span id="a56105-message-selector"></span> `message-selector` | `VALUE` | `String` |   | JMS API message selector expression based on a subset of the SQL92 |
| <span id="a4cfbb-named-factory"></span> `named-factory` | `VALUE` | `String` |   | Select `jakarta.jms.ConnectionFactory ConnectionFactory` in case factory is injected as a named bean or configured with name |
| <span id="a1e92c-non-local"></span> `non-local` | `VALUE` | `Boolean` | `false` | When set to `true`, messages published by this connection, or any connection with the same client identifier, will not be delivered to this durable subscription |
| <span id="a9446b-password"></span> `password` | `VALUE` | `String` |   | Password used for creating JMS connection |
| <span id="a4c7fd-period-executions"></span> `period-executions` | `VALUE` | `Long` | `100` | Period for executing poll cycles in millis |
| <span id="abdb87-poll-timeout"></span> `poll-timeout` | `VALUE` | `Long` | `50` | Timeout for polling for next message in every poll cycle in millis |
| <span id="a5bcad-queue"></span> `queue` | `VALUE` | `String` |   | Use supplied destination name and `Type#QUEUE QUEUE` as type |
| <span id="aad6ba-session-group-id"></span> `session-group-id` | `VALUE` | `String` |   | When multiple channels share same session-group-id, they share same JMS session |
| <span id="a0166f-subscriber-name"></span> `subscriber-name` | `VALUE` | `String` |   | Subscriber name used to identify a durable subscription |
| <span id="a82a72-topic"></span> `topic` | `VALUE` | `String` |   | Use supplied destination name and `Type#TOPIC TOPIC` as type |
| <span id="a5eafc-transacted"></span> `transacted` | `VALUE` | `Boolean` | `false` | Indicates whether the session will use a local transaction |
| <span id="aa2815-type"></span> [`type`](../../config/io_helidon_messaging_connectors_jms_Type.md) | `VALUE` | `i.h.m.c.j.Type` | `QUEUE` | Specify if connection is `io.helidon.messaging.connectors.jms.Type#QUEUE queue` or `io.helidon.messaging.connectors.jms.Type#TOPIC topic` |
| <span id="a42b8b-username"></span> `username` | `VALUE` | `String` |   | User name used for creating JMS connection |

### Configured JMS Factory

The simplest possible usage is leaving construction of `AQjmsConnectionFactory` to the connector.

*Example of connector config:*

``` yaml
mp:
  messaging:

    connector:
      helidon-aq:
        transacted: false
        acknowledge-mode: CLIENT_ACKNOWLEDGE
        url: jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(Host=192.168.0.123)(Port=1521))(CONNECT_DATA=(SID=TESTSID)))
        user: gandalf
        password: mellon

    outgoing.to-aq:
      connector: helidon-aq
      destination: TESTQUEUE
      type: queue

    incoming.from-aq:
      connector: helidon-aq
      destination: TESTQUEUE
      type: queue
```

Its also possible and preferable to refer to [configured datasource](../persistence.md), in our example [Oracle UCP datasource](../persistence.md):

*Example of connector config with Oracle UCP datasource:*

``` yaml
javax:
  sql:
    DataSource:
      aq-test-ds:
        connectionFactoryClassName: oracle.jdbc.pool.OracleDataSource
        URL: jdbc:oracle:thin:@exampledb_high?TNS_ADMIN=/home/gandalf/wallets/Wallet_EXAMPLEDB
        user: gandalf
        password: SuperSecretPassword1234

mp:
  messaging:
    connector:
      helidon-aq:
        transacted: false
        acknowledge-mode: CLIENT_ACKNOWLEDGE
        data-source: aq-test-ds
    outgoing.toJms:
      connector: helidon-aq
      destination: TESTQUEUE
      type: queue
    incoming.fromJms:
      connector: helidon-aq
      destination: TESTQUEUE
      type: queue
```

### Injected JMS factory

If you need more advanced configurations, connector can work with injected `AQjmsConnectionFactory`:

*Inject:*

``` java
@Produces
@ApplicationScoped
@Named("aq-orderdb-factory")
public AQjmsConnectionFactory connectionFactory() throws JMSException {
    AQjmsQueueConnectionFactory fact = new AQjmsQueueConnectionFactory();
    fact.setJdbcURL(config.get("jdbc.url").asString().get());
    fact.setUsername(config.get("jdbc.user").asString().get());
    fact.setPassword(config.get("jdbc.pass").asString().get());
    return fact;
}
```

*Config:*

``` yaml
jdbc:
  url: jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(Host=192.168.0.123)(Port=1521))(CONNECT_DATA=(SID=TESTSID)))
  user: gandalf
  pass: mellon

mp:
  messaging:
    connector:
      helidon-aq:
        named-factory: aq-orderdb-factory

    outgoing.to-aq:
      connector: helidon-aq
      session-group-id: order-connection-1
      destination: TESTQUEUE
      type: queue

    incoming.from-aq:
      connector: helidon-aq
      session-group-id: order-connection-1
      destination: TESTQUEUE
      type: queue
```

## Usage

### Consuming

*Consuming one by one unwrapped value:*

``` java
@Incoming("from-aq")
public void consumeAq(String msg) {
    System.out.println("Oracle AQ says: " + msg);
}
```

*Consuming one by one, manual ack:*

``` java
@Incoming("from-aq")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> consumeAq(AqMessage<String> msg) {
    // direct commit
    //msg.getDbConnection().commit();
    System.out.println("Oracle AQ says: " + msg.getPayload());
    // ack commits only in non-transacted mode
    return msg.ack();
}
```

### Producing

*Producing to AQ:*

``` java
@Outgoing("to-aq")
public PublisherBuilder<String> produceToAq() {
    return ReactiveStreams.of("test1", "test2");
}
```
