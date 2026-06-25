<!--@frontmatter
description: "Reactive Messaging Mock connector for testing"
-->
# Mock Connector

## Overview

Mock connector is a simple application scoped bean that can be used for emitting
to a channel or asserting received data in a test environment. All data received
are kept in memory only.

## Maven Coordinates

To enable Mock Connector, add the following dependency to your project’s
`pom.xml` (see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.messaging.mock</groupId>
  <artifactId>helidon-messaging-mock</artifactId>
</dependency>
```

## Usage

> [!WARNING]
> Mock connector should be used in the test environment only!

For injecting Mock Connector use `@TestConnector` qualifier:

```java
@Inject
@TestConnector
MockConnector mockConnector;
```

### Emitting Data

Emitting String values a, b, c:

<!--@mdc ::code-callout -->
```java
mockConnector.incoming("my-incoming-channel", String.class) // <1>
        .emit("a", "b", "c");
```
1. Get incoming channel of given name and payload type
<!--@mdc :: -->

### Asserting Data

Awaiting and asserting payloads with custom mapper:

<!--@mdc ::code-callout -->
```java
mockConnector
        .outgoing("my-outgoing-channel", String.class) // <1>
        .awaitData(TIMEOUT, Message::getPayload, "a", "b", "c"); // <2>
```
1. Get outgoing channel of given name and payload type
2. Request number of expected items and block the thread until items arrive then
   assert the payloads
<!--@mdc :: -->

## Configuration

| Key            | Default value    | Description                                                        |
|----------------|------------------|--------------------------------------------------------------------|
| mock-data      |                  | Initial data emitted to the channel immediately after subscription |
| mock-data-type | java.lang.String | Type of the emitted initial data to be emitted                     |

## Helidon Test with Mock Connector

Mock connector works great with built-in Helidon test support for [JUnit
5][junit-5] or [TestNG][testng].

As Helidon test support makes a bean out of your test, you can inject
MockConnector directly into it.

<!--@mdc ::code-callout{collapsed} -->
```java
@HelidonTest
@DisableDiscovery // <1>
@AddBean(MockConnector.class) // <2>
@AddExtension(MessagingCdiExtension.class) // <3>
@AddConfig(key = "mp.messaging.incoming.test-channel-in.connector", value = MockConnector.CONNECTOR_NAME) // <4>
@AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data-type", value = "java.lang.Integer") // <5>
@AddConfig(key = "mp.messaging.incoming.test-channel-in.mock-data", value = "6,7,8") // <6>
@AddConfig(key = "mp.messaging.outgoing.test-channel-out.connector", value = MockConnector.CONNECTOR_NAME) // <7>
public class MessagingTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    @TestConnector
    private MockConnector mockConnector; // <8>

    @Incoming("test-channel-in")
    @Outgoing("test-channel-out")
    int multiply(int payload) {  // <9>
        return payload * 10;
    }

    @Test
    void testMultiplyChannel() {
        mockConnector.outgoing("test-channel-out", Integer.TYPE) // <10>
                .awaitPayloads(TIMEOUT, 60, 70, 80);
    }
}
```
1. If you want to add all the beans manually
2. Manually add MockConnector bean, so it is accessible by messaging for
   constructing the channels
3. Messaging support in Helidon MP is provided by this CDI extension
4. Instruct messaging to use `mock-connector` as an upstream for channel
   `test-channel-in`
5. Generate mock data of `java.lang.Integer`, String is default
6. Generate mock data
7. Instruct messaging to use `mock-connector` as a downstream for channel
   `test-channel-out`
8. Inject mock connector so we can access publishers and subscribers registered
   within the mock connector
9. Messaging processing method connecting together channels `test-channel-in` and
   `test-channel-out`
10. Actual JUnit 5 test method which is going to block the thread until 3 items
   are intercepted on `test-channel-out` channel’s downstream and assert those
   with expected values.
<!--@mdc :: -->

[junit-5]: ../testing/testing.md
[testng]: ../testing/testing-ng.md
