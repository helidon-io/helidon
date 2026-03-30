# Mock Connector

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [Configuration](#configuration)
- [Helidon Test](#helidon-test-with-mock-connector)

## Overview

Mock connector is a simple application scoped bean that can be used for emitting to a channel or asserting received data in a test environment. All data received are kept in memory only.

## Maven Coordinates

To enable Mock Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.messaging.mock</groupId>
    <artifactId>helidon-messaging-mock</artifactId>
</dependency>
```

## Usage

> [!WARNING]
> Mock connector should be used in the test environment only!

For injecting Mock Connector use `@TestConnector` qualifier:

``` java
@Inject
@TestConnector
MockConnector mockConnector;
```

### Emitting Data

*Emitting String values `a`, `b`, `c`*

``` java
mockConnector.incoming("my-incoming-channel", String.class) 
        .emit("a", "b", "c");
```

- Get incoming channel of given name and payload type

### Asserting Data

*Awaiting and asserting payloads with custom mapper*

``` java
mockConnector
        .outgoing("my-outgoing-channel", String.class) 
        .awaitData(TIMEOUT, Message::getPayload, "a", "b", "c"); 
```

- Get outgoing channel of given name and payload type
- Request number of expected items and block the thread until items arrive then assert the payloads

## Configuration

|  |  |  |
|----|----|----|
| Key | Default value | Description |
| mock-data |  | Initial data emitted to the channel immediately after subscription |
| mock-data-type | java.lang.String | Type of the emitted initial data to be emitted |

## Helidon Test with Mock Connector

Mock connector works great with built-in Helidon test support for [JUnit 5](/../../mp/reactivemessaging/../../testing/testing.adoc) or [TestNG](/../../mp/reactivemessaging/../../testing/testing-ng.adoc).

As Helidon test support makes a bean out of your test, you can inject MockConnector directly into it.

``` java
@HelidonTest
@DisableDiscovery 
@AddBean(MockConnector.class) 
@AddExtension(MessagingCdiExtension.class) 
@AddConfig(key = "mp.messaging.incoming.test-channel-in.connector", value = MockConnector.CONNECTOR_NAME) 
@AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data-type", value = "java.lang.Integer") 
@AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data", value = "6,7,8") 
@AddConfig(key = "mp.messaging.outgoing.test-channel-out.connector", value = MockConnector.CONNECTOR_NAME) 
public class MessagingTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    @TestConnector
    private MockConnector mockConnector; 

    @Incoming("test-channel-in")
    @Outgoing("test-channel-out")
    int multiply(int payload) {  
        return payload * 10;
    }

    @Test
    void testMultiplyChannel() {
        mockConnector.outgoing("test-channel-out", Integer.TYPE) 
                .awaitPayloads(TIMEOUT, 60, 70, 80);
    }
}
```

- If you want to add all the beans manually
- Manually add MockConnector bean, so it is accessible by messaging for constructing the channels
- Messaging support in Helidon MP is provided by this CDI extension
- Instruct messaging to use `mock-connector` as an upstream for channel `test-channel-in`
- Generate mock data of `java.lang.Integer`, String is default
- Generate mock data
- Instruct messaging to use `mock-connector` as a downstream for channel `test-channel-out`
- Inject mock connector so we can access publishers and subscribers registered within the mock connector
- Messaging processing method connecting together channels `test-channel-in` and `test-channel-out`
- Actual JUnit 5 test method which is going to block the thread until 3 items are intercepted on `test-channel-out` channel’s downstream and assert those with expected values.
