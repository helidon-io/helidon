/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.messaging.spi.ConnectorConfig;
import io.helidon.messaging.spi.ConnectorDelivery;
import io.helidon.messaging.spi.ConnectorDeliveryReservation;
import io.helidon.messaging.spi.ConnectorDirection;
import io.helidon.messaging.spi.ConnectorProvider;
import io.helidon.messaging.spi.IncomingConnector;
import io.helidon.messaging.spi.IncomingConnectorContext;
import io.helidon.messaging.spi.IncomingConnectorProvider;
import io.helidon.messaging.spi.OutgoingConnector;
import io.helidon.messaging.spi.OutgoingConnectorProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ChannelRegistryFailurePolicyTest {
    private static final String NON_PORTABLE_FAILURE_TYPE_HEADER =
            "helidon_messaging_dead_letter_failure_type";
    private static final String NON_PORTABLE_FAILURE_MESSAGE_HEADER =
            "helidon_messaging_dead_letter_failure_message";

    private final List<ChannelRegistry> startedRegistries = new ArrayList<>();

    @AfterEach
    void closeStartedRegistries() {
        Throwable closeFailure = null;
        for (int i = startedRegistries.size() - 1; i >= 0; i--) {
            try {
                startedRegistries.get(i).close();
            } catch (RuntimeException | Error e) {
                if (closeFailure == null) {
                    closeFailure = e;
                } else if (closeFailure != e) {
                    closeFailure.addSuppressed(e);
                }
            }
        }
        startedRegistries.clear();
        if (closeFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (closeFailure instanceof Error error) {
            throw error;
        }
    }

    @Test
    void testGlobalAndChannelExecutionConfigurationMerge() {
        Config config = yaml("""
                helidon:
                  messaging:
                    execution:
                      queue-capacity: 3
                      max-pending-admissions: 4
                      max-pending-messages: 5
                      max-in-flight-messages: 7
                      admission-timeout: PT0.009S
                      shutdown-timeout: PT0.01S
                    channel:
                      orders:
                        execution:
                          queue-capacity: 12
                          max-pending-messages: 14
                          admission-timeout: PT0.018S
                """);

        MessagingExecutionConfig global = ChannelRegistry.executionConfig(config, null);
        assertThat(global.queueCapacity(), is(3));
        assertThat(global.maxPendingAdmissions(), is(4));
        assertThat(global.maxPendingMessages(), is(5));
        assertThat(global.maxInFlightMessages(), is(7));
        assertThat(global.admissionTimeout(), is(java.util.Optional.of(Duration.ofMillis(9))));
        assertThat(global.shutdownTimeout(), is(Duration.ofMillis(10)));

        MessagingExecutionConfig orders = ChannelRegistry.executionConfig(config, "orders");
        assertThat(orders.queueCapacity(), is(12));
        assertThat(orders.maxPendingAdmissions(), is(4));
        assertThat(orders.maxPendingMessages(), is(14));
        assertThat(orders.maxInFlightMessages(), is(7));
        assertThat(orders.admissionTimeout(), is(java.util.Optional.of(Duration.ofMillis(18))));
        assertThat(orders.shutdownTimeout(), is(Duration.ofMillis(10)));
    }

    @Test
    void testLiteralDottedChannelExecutionConfigurationMerge() {
        Config config = yaml("""
                helidon:
                  messaging:
                    execution:
                      queue-capacity: 3
                      max-pending-messages: 5
                    channel:
                      orders~1v1:
                        execution:
                          queue-capacity: 12
                          max-pending-messages: 14
                """);

        MessagingExecutionConfig orders = ChannelRegistry.executionConfig(config, "orders.v1");

        assertThat(orders.queueCapacity(), is(12));
        assertThat(orders.maxPendingMessages(), is(14));
    }

    @Test
    void testChannelCannotOverrideGlobalShutdownTimeout() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(
                        List.of(registration("orders", ignored -> { })),
                        yaml("""
                                helidon:
                                  messaging:
                                    channel:
                                      orders:
                                        execution:
                                          shutdown-timeout: PT1S
                                """),
                        List.of()));

        assertThat(failure.getMessage(), containsString("must not override global shutdown-timeout"));
    }

    @Test
    void testLiteralDottedChannelInvokesConnectorProviders() {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        ChannelRegistry registry = registry(
                List.of(),
                yaml("""
                        helidon:
                          messaging:
                            incoming:
                              orders~1v1:
                                connector: test-in
                                destination: orders-v1
                            outgoing:
                              orders~1v1:
                                connector: test-out
                        """),
                List.of(incoming, outgoing));
        try {
            assertThat(incoming.createdCount(), is(1));
            assertThat(outgoing.createdCount(), is(1));
            TestConnectorConfig connectorConfig = incoming.config("orders.v1");
            assertThat(connectorConfig.channelName(), is("orders.v1"));
            assertThat(connectorConfig.connector(), is("test-in"));
            assertThat(connectorConfig.properties().get("destination"), is("orders-v1"));
        } finally {
            registry.close();
        }
    }

    @Test
    void testLiteralDottedConnectorDefaultsAndChannelOverrides() {
        TestIncomingConnector incoming = new TestIncomingConnector("acme.v1");
        ChannelRegistry registry = registry(
                List.of(registration("inherited", ignored -> { }),
                        registration("overridden", ignored -> { }),
                        registration("empty", ignored -> { })),
                yaml("""
                        helidon:
                          messaging:
                            connector:
                              acme~1v1:
                                endpoint: https://default.example.test
                                items: [a, b, c]
                                authentication:
                                  username: connector-user
                                  password: connector-password
                                failure: [ignored]
                            incoming:
                              inherited:
                                connector: acme.v1
                              overridden:
                                connector: acme.v1
                                endpoint: https://channel.example.test
                                items: [x]
                                authentication:
                                  username: channel-user
                                failure:
                                  retry:
                                    max-attempts: 1
                              empty:
                                connector: acme.v1
                                items: []
                        """),
                List.of(incoming));
        try {
            assertThat(incoming.createdCount(), is(3));

            TestConnectorConfig inherited = incoming.config("inherited");
            assertThat(inherited.connector(), is("acme.v1"));
            assertThat(inherited.properties().get("endpoint"), is("https://default.example.test"));
            assertThat(inherited.properties().get("authentication.username"), is("connector-user"));
            assertThat(inherited.properties().get("authentication.password"), is("connector-password"));
            assertThat(inherited.config().get("items").asList(String.class).get(), is(List.of("a", "b", "c")));
            assertThat(inherited.config().get("failure").exists(), is(false));

            TestConnectorConfig overridden = incoming.config("overridden");
            assertThat(overridden.connector(), is("acme.v1"));
            assertThat(overridden.properties().get("endpoint"), is("https://channel.example.test"));
            assertThat(overridden.properties().get("authentication.username"), is("channel-user"));
            assertThat(overridden.properties().get("authentication.password"), is("connector-password"));
            assertThat(overridden.config().get("items").asList(String.class).get(), is(List.of("x")));
            assertThat(overridden.config().get("failure").exists(), is(false));

            Config emptyItems = incoming.config("empty").config().get("items");
            assertThat(emptyItems.exists(), is(true));
            assertThat(emptyItems.type(), is(Config.Type.LIST));
            assertThat(emptyItems.asList(String.class).get(), is(List.of()));
            assertThat(incoming.config("empty").config().get("failure").exists(), is(false));
        } finally {
            registry.close();
        }
    }

    @Test
    void testConnectorConfigMergePreservesHybridValues() {
        TestIncomingConnector incoming = new TestIncomingConnector();
        ConfigNode.ObjectNode defaultConnector = ConfigNode.ObjectNode.builder()
                .addObject("settings", ConfigNode.ObjectNode.builder()
                        .value("default")
                        .addValue("child", "nested")
                        .build())
                .addList("items", ConfigNode.ListNode.builder()
                        .value("default-list")
                        .addValue("a")
                        .addValue("b")
                        .build())
                .build();
        ConfigNode.ObjectNode channel = ConfigNode.ObjectNode.builder()
                .addValue("connector", "test-in")
                .addObject("settings", ConfigNode.ObjectNode.builder()
                        .value("channel")
                        .build())
                .addList("items", ConfigNode.ListNode.builder()
                        .value("channel-list")
                        .addValue("x")
                        .build())
                .build();
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("helidon", ConfigNode.ObjectNode.builder()
                        .addObject("messaging", ConfigNode.ObjectNode.builder()
                                .addObject("connector", ConfigNode.ObjectNode.builder()
                                        .addObject("test-in", defaultConnector)
                                        .build())
                                .addObject("incoming", ConfigNode.ObjectNode.builder()
                                        .addObject("orders", channel)
                                        .build())
                                .build())
                        .build())
                .build();
        Config config = Config.just(ConfigSources.create(root));
        ChannelRegistry registry = registry(
                List.of(registration("orders", ignored -> { })),
                config,
                List.of(incoming));
        try {
            Config settings = incoming.config("orders").config().get("settings");
            assertThat(settings.type(), is(Config.Type.OBJECT));
            assertThat(settings.asString().get(), is("channel"));
            assertThat(settings.get("child").asString().get(), is("nested"));
            Config items = incoming.config("orders").config().get("items");
            assertThat(items.type(), is(Config.Type.LIST));
            assertThat(items.asString().get(), is("channel-list"));
            assertThat(items.asList(String.class).get(), is(List.of("x")));
        } finally {
            registry.close();
        }
    }

    @Test
    void testProcessorRouteBoundsConnectorDeliveryAndUnlimitedRetryCompletes() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger processorAttempts = new AtomicInteger();
        List<String> received = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(
                List.of(passThroughProcessor("source", "target", processorAttempts),
                        registration("target", message -> received.add((String) message.entity()))),
                yaml("""
                        helidon:
                          messaging:
                            channel:
                              source:
                                execution:
                                  max-pending-messages: 2
                                  max-in-flight-messages: 2
                              target:
                                execution:
                                  max-pending-messages: 8
                                  max-in-flight-messages: 1
                            incoming:
                              source:
                                connector: test-in
                        """),
                List.of(incoming));
        start(registry);
        try {
            IncomingConnectorContext context = incoming.context("source");
            int advertised = context.maxDeliveryMessages();
            List<Message<String>> messages = new ArrayList<>(advertised);
            for (int i = 0; i < advertised; i++) {
                messages.add(Message.create("message-" + i));
            }
            try (ConnectorDeliveryReservation reservation = context.reserveDelivery();
                 ConnectorDelivery delivery = reservation.start(MessageBatch.create(messages))) {
                assertThat(delivery.await(Duration.ofSeconds(2)), is(true));
            }

            assertThat(advertised, is(1));
            assertThat(processorAttempts.get(), is(1));
            assertThat(received, is(List.of("message-0")));

            try (ConnectorDeliveryReservation reservation = context.reserveDelivery()) {
                MessagingRejectedException oversized = assertThrows(
                        MessagingRejectedException.class,
                        () -> reservation.start(MessageBatch.create(List.of(Message.create("one"),
                                                                          Message.create("two")))));
                assertThat(oversized.reason(), is(MessagingRejectedException.Reason.OVERSIZED));
            }
            assertThat(processorAttempts.get(), is(1));
        } finally {
            registry.close();
        }
    }

    @Test
    void testTryReserveDeliveryUsesConfiguredSharedAdmissionTimeoutBudget() {
        Duration configuredTimeout = Duration.ofMillis(500);
        Duration testTimeout = Duration.ofSeconds(5);
        TestIncomingConnector incoming = new TestIncomingConnector();
        ChannelRegistry registry = registry(
                List.of(registration("orders", ignored -> { })),
                yaml("""
                        helidon:
                          messaging:
                            channel:
                              orders:
                                execution:
                                  max-pending-messages: 1
                                  max-in-flight-messages: 1
                                  admission-timeout: %s
                            incoming:
                              orders:
                                connector: test-in
                        """.formatted(configuredTimeout)),
                List.of(incoming));
        start(registry);
        IncomingConnectorContext context = incoming.context("orders");

        try (ConnectorDeliveryReservation held = context.reserveDelivery()) {
            FutureTask<MessagingRejectedException> timeoutProbe = new FutureTask<>(
                    () -> awaitConfiguredTimeout(context, configuredTimeout, testTimeout));
            MessagingRejectedException timeout = awaitTimeoutProbe(timeoutProbe);
            assertThat(timeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));

            MessagingRejectedException stickyTimeout = assertThrows(MessagingRejectedException.class,
                                                                      context::tryReserveDelivery);
            assertThat(stickyTimeout.reason(), is(MessagingRejectedException.Reason.TIMEOUT));
        }

        try (ConnectorDeliveryReservation ignored = context.reserveDelivery()) {
            // Reset the expired non-blocking budget and release the reservation.
        }
        var available = context.tryReserveDelivery();
        assertThat(available.isPresent(), is(true));
        try (ConnectorDeliveryReservation ignored = available.orElseThrow()) {
            // Release the successful non-blocking reservation.
        }
    }

    @Test
    void testCoordinatorKeepsLargeFailureDiagnosticsLocalAndSettlesDeadLetter() throws InterruptedException {
        int maxPortableHeaderLength = 64;
        String failureMessage = "credential=top-secret\r\nforged-header=true\n" + "x".repeat(4096);
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector(maxPortableHeaderLength);
        AtomicInteger attempts = new AtomicInteger();
        ChannelRegistry registry = registry(List.of(registration("orders", ignored -> {
                                attempts.incrementAndGet();
                                throw new IllegalStateException(failureMessage);
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        channel:
                                          orders:
                                            execution:
                                              queue-capacity: 0
                                              max-pending-messages: 1
                                              max-in-flight-messages: 1
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT0.001S
                                                max-attempts: 2
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);
        Message<String> original = Message.builder("order-1")
                .header("trace-id", "trace-1")
                .localMetadata(MessageMetadata.builder()
                                       .set("application.local.delivery", "delivery-1")
                                       .set(DeadLetterMessage.FAILURE_TYPE_METADATA, "spoofed-type")
                                       .set(DeadLetterMessage.FAILURE_MESSAGE_METADATA, "spoofed-message")
                                       .build())
                .build();

        deliver(incoming.context("orders"), MessageBatch.create(original));
        deliver(incoming.context("orders"), MessageBatch.create(Message.create("order-2")));

        TestConnectorConfig connectorConfig = incoming.config("orders");
        assertThat(connectorConfig.properties().keySet().stream()
                           .noneMatch(key -> key.equals("failure") || key.startsWith("failure.")), is(true));
        assertThat(attempts.get(), is(4));
        assertThat(outgoing.sendCount(), is(2));
        assertThat(outgoing.messages().size(), is(2));
        DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) outgoing.messages().getFirst();
        assertThat(deadLetter.originalMessage(), sameInstance(original));
        assertThat(deadLetter.attempts(), is(2));
        assertThat(deadLetter.failureType(), is(IllegalStateException.class.getName()));
        assertThat(deadLetter.failureMessage(), is(failureMessage));
        assertThat(deadLetter.localMetadata().text("application.local.delivery").orElseThrow(), is("delivery-1"));
        assertThat(deadLetter.localMetadata().text(DeadLetterMessage.FAILURE_TYPE_METADATA).orElseThrow(),
                   is(IllegalStateException.class.getName()));
        assertThat(deadLetter.localMetadata().text(DeadLetterMessage.FAILURE_MESSAGE_METADATA).orElseThrow(),
                   is(failureMessage));
        assertThat(deadLetter.headers().contains(NON_PORTABLE_FAILURE_TYPE_HEADER), is(false));
        assertThat(deadLetter.headers().contains(NON_PORTABLE_FAILURE_MESSAGE_HEADER), is(false));
        assertThat(deadLetter.headers().entries().stream()
                           .map(MessageHeader::value)
                           .filter(HeaderValue.TextValue.class::isInstance)
                           .map(HeaderValue.TextValue.class::cast)
                           .allMatch(value -> value.value().length() <= maxPortableHeaderLength), is(true));
        assertThat(deadLetter.headers().entries().stream()
                           .map(MessageHeader::value)
                           .filter(HeaderValue.TextValue.class::isInstance)
                           .map(HeaderValue.TextValue.class::cast)
                           .noneMatch(value -> value.value().contains("top-secret")), is(true));
    }

    @Test
    void testUnlimitedPreDispatchFailureTerminatesAndRetainsCapacityUntilClose() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        AtomicInteger entityCalls = new AtomicInteger();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                handled.add((String) message.entity());
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        channel:
                                          orders:
                                            execution:
                                              queue-capacity: 0
                                              max-pending-messages: 2
                                              max-in-flight-messages: 1
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT1H
                                    """),
                            List.of(incoming));
        start(registry);
        IncomingConnectorContext context = incoming.context("orders");
        MessageBatch<String> failedBatch = MessageBatch.create(new Message<>() {
            @Override
            public String entity() {
                entityCalls.incrementAndGet();
                throw new MessagingException("entity unavailable");
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.builder().add("message-id", "poison-1").build();
            }
        });
        MessageBatch<String> goodBatch = MessageBatch.create(Message.create("good"));
        IllegalStateException mappingFailure = new IllegalStateException("message mapping failed");

        try (ConnectorDeliveryReservation reservation = context.reserveDelivery();
             ConnectorDelivery delivery = reservation.startFailed(failedBatch, mappingFailure)) {
            BatchDeliveryException terminalFailure = assertThrows(
                    BatchDeliveryException.class,
                    () -> delivery.await(Duration.ofSeconds(2)));
            assertThat(delivery.isDone(), is(true));
            assertThat(terminalFailure.batch(), sameInstance(failedBatch));
            assertThat(terminalFailure.getCause(), sameInstance(mappingFailure));
            assertThat(handled, is(List.of()));
            assertThat(entityCalls.get(), is(0));

            try (ConnectorDeliveryReservation blocked = context.reserveDelivery()) {
                assertThat(blocked.tryStart(goodBatch).isEmpty(), is(true));
                delivery.close();
                try (ConnectorDelivery goodDelivery = blocked.tryStart(goodBatch).orElseThrow()) {
                    assertThat(goodDelivery.await(Duration.ofSeconds(2)), is(true));
                }
            }
        }

        assertThat(handled, is(List.of("good")));
    }

    @Test
    void testPreDispatchEntityFailureReachesLocalDeadLetterEnvelopeConsumer() throws InterruptedException {
        assertPreDispatchEntityFailureReachesLocalConsumer(
                DeadLetterMessage.class,
                new GenericType<DeadLetterMessage<String>>() { });
    }

    @Test
    void testPreDispatchEntityFailureReachesLocalMessageEnvelopeConsumer() throws InterruptedException {
        assertPreDispatchEntityFailureReachesLocalConsumer(
                Message.class,
                new GenericType<Message<String>>() { });
    }

    private void assertPreDispatchEntityFailureReachesLocalConsumer(Class<?> envelopeType,
                                                                    GenericType<?> envelopeGenericType)
            throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger entityCalls = new AtomicInteger();
        Message<String> unavailable = new Message<>() {
            @Override
            public String entity() {
                entityCalls.incrementAndGet();
                throw new MessagingException("entity unavailable");
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.builder().add("message-id", "poison-1").build();
            }
        };
        AtomicReference<Message<?>> routed = new AtomicReference<>();
        ConsumerRegistration deadLetterConsumer = registration(
                "orders-dlq",
                String.class,
                new GenericType<String>() { },
                envelopeType,
                envelopeGenericType,
                routed::set);
        ChannelRegistry registry = registry(
                List.of(registration("orders", ignored -> {
                            throw new AssertionError("Unavailable payload must not reach handlers");
                        }),
                        deadLetterConsumer),
                yaml("""
                        helidon:
                          messaging:
                            incoming:
                              orders:
                                connector: test-in
                                failure:
                                  retry:
                                    max-attempts: 1
                                  on-exhausted: DEAD_LETTER
                                  dead-letter:
                                    channel: orders-dlq
                        """),
                List.of(incoming));
        start(registry);
        try {
            IllegalStateException mappingFailure = new IllegalStateException("message mapping failed");
            MessageBatch<String> failedBatch = MessageBatch.create(unavailable);
            assertThat(entityCalls.get(), is(0));

            deliverFailed(incoming.context("orders"), failedBatch, mappingFailure);

            DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) routed.get();
            assertThat(deadLetter.originalMessage(), sameInstance(unavailable));
            assertThat(deadLetter.failureMessage(), is(mappingFailure.getMessage()));
            assertThat(entityCalls.get(), is(2));
        } finally {
            registry.close();
        }
    }

    @Test
    void testPreDispatchFailureRetriesThenDeadLettersWithoutInvokingHandlers() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        AtomicInteger handlerCalls = new AtomicInteger();
        ChannelRegistry registry = registry(List.of(registration("orders", ignored -> {
                                handlerCalls.incrementAndGet();
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        channel:
                                          orders:
                                            execution:
                                              max-pending-messages: 2
                                              max-in-flight-messages: 2
                                          orders-dlq:
                                            execution:
                                              max-pending-messages: 8
                                              max-in-flight-messages: 1
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT0.001S
                                                max-attempts: 3
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);
        try {
            IncomingConnectorContext context = incoming.context("orders");
            assertThat(context.maxDeliveryMessages(), is(1));
            Message<String> rejected = Message.builder("unmapped").header("message-id", "poison-1").build();
            IllegalStateException mappingFailure = new IllegalStateException("message mapping failed");

            deliverFailed(context, MessageBatch.create(rejected), mappingFailure);

            assertThat(handlerCalls.get(), is(0));
            assertThat(outgoing.messages().size(), is(1));
            DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) outgoing.messages().getFirst();
            assertThat(deadLetter.originalMessage(), sameInstance(rejected));
            assertThat(deadLetter.attempts(), is(3));
            assertThat(deadLetter.failureType(), is(mappingFailure.getClass().getName()));
            assertThat(deadLetter.failureMessage(), is(mappingFailure.getMessage()));
        } finally {
            registry.close();
        }
    }

    @Test
    void testPreDispatchFailureDropsWithoutInvokingHandlersOrFailingGraph() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                handled.add((String) message.entity());
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DROP
                                    """),
                            List.of(incoming));
        start(registry);
        IncomingConnectorContext context = incoming.context("orders");

        deliverFailed(context,
                      MessageBatch.create(Message.create("unmapped")),
                      new IllegalStateException("message mapping failed"));
        deliver(context, MessageBatch.create(Message.create("good")));

        assertThat(handled, is(List.of("good")));
    }

    @Test
    void testStructuredPreDispatchFailureDeadLettersBeforeDispatchingDeferredSiblings() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        AtomicBoolean failureSettledBeforeDeferred = new AtomicBoolean();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                handled.add((String) message.entity());
                                failureSettledBeforeDeferred.compareAndSet(false, outgoing.messages().size() == 1);
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);
        MessageBatch<String> root = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("unmapped"),
                                                                 Message.create("third")));
        IllegalStateException mappingFailure = new IllegalStateException("mapping failed");

        deliverFailed(incoming.context("orders"), root, mixedPreDispatchFailure(root, mappingFailure));

        assertThat(handled, is(List.of("first", "third")));
        assertThat(failureSettledBeforeDeferred.get(), is(true));
        assertThat(outgoing.messages().size(), is(1));
        DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) outgoing.messages().getFirst();
        assertThat(deadLetter.originalMessage(), sameInstance(root.get(1)));
        assertThat(deadLetter.attempts(), is(1));
        assertThat(deadLetter.failureType(), is(mappingFailure.getClass().getName()));
        assertThat(deadLetter.failureMessage(), is(mappingFailure.getMessage()));
    }

    @Test
    void testStructuredPreDispatchFailStopsBeforeDeferredSiblings() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                handled.add((String) message.entity());
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                    """),
                            List.of(incoming));
        start(registry);
        MessageBatch<String> root = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("unmapped"),
                                                                 Message.create("third")));
        IllegalStateException mappingFailure = new IllegalStateException("mapping failed");

        BatchDeliveryException terminal = assertThrows(
                BatchDeliveryException.class,
                () -> deliverFailed(incoming.context("orders"), root, mixedPreDispatchFailure(root, mappingFailure)));

        assertThat(terminal.batch(), sameInstance(root));
        assertThat(terminal.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.NOT_ATTEMPTED,
                              BatchItemStatus.FAILED,
                              BatchItemStatus.NOT_ATTEMPTED)));
        assertThat(terminal.outcome(1).failure().orElseThrow(), sameInstance(mappingFailure));
        assertThat(handled, is(List.of()));
    }

    @Test
    void testAllNotAttemptedPreDispatchFailureConsumesAttemptsAndDeadLetters() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        AtomicInteger handlerCalls = new AtomicInteger();
        ChannelRegistry registry = registry(List.of(registration("orders", ignored -> {
                                handlerCalls.incrementAndGet();
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT0.001S
                                                max-attempts: 2
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);
        MessageBatch<String> root = MessageBatch.create(List.of(Message.create("first"), Message.create("second")));
        IllegalStateException mappingFailure = new IllegalStateException("mapping failed before dispatch");

        try (ConnectorDeliveryReservation reservation = incoming.context("orders").reserveDelivery();
             ConnectorDelivery delivery = reservation.startFailed(
                     root,
                     BatchDeliveryExceptionSupport.notAttempted("Mapping", root, mappingFailure))) {
            assertThat(delivery.await(Duration.ofSeconds(2)), is(true));
        }

        assertThat(handlerCalls.get(), is(0));
        assertThat(outgoing.messages().size(), is(2));
        for (int i = 0; i < root.size(); i++) {
            DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) outgoing.messages().get(i);
            assertThat(deadLetter.originalMessage(), sameInstance(root.get(i)));
            assertThat(deadLetter.attempts(), is(2));
            assertThat(deadLetter.failureType(), is(mappingFailure.getClass().getName()));
            assertThat(deadLetter.failureMessage(), is(mappingFailure.getMessage()));
        }
    }

    @Test
    void testCoordinatorDeadLettersCustomMessageImplementations() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        ChannelRegistry registry = registry(List.of(batchRegistration("orders", ignored -> {
                                throw new IllegalStateException("handler failed");
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);
        Message<String> first = customMessage("order-1");
        Message<String> second = customMessage("order-2");

        deliver(incoming.context("orders"), MessageBatch.create(List.of(first, second)));

        assertThat(outgoing.messages().size(), is(2));
        assertThat(outgoing.messages().stream().allMatch(DeadLetterMessage.class::isInstance), is(true));
        assertThat(((DeadLetterMessage<?>) outgoing.messages().get(0)).originalMessage(), sameInstance(first));
        assertThat(((DeadLetterMessage<?>) outgoing.messages().get(1)).originalMessage(), sameInstance(second));
    }

    @Test
    void testCoordinatorSettlesAttemptedSubsetBeforeDeferredSubset() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        AtomicBoolean failedSubsetSettledBeforeDeferred = new AtomicBoolean();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                String entity = (String) message.entity();
                                handled.add(entity);
                                if (entity.equals("poison")) {
                                    throw new IllegalStateException("application failed");
                                }
                                if (entity.equals("deferred")) {
                                    failedSubsetSettledBeforeDeferred.set(outgoing.messages().size() == 1);
                                }
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);

        deliver(incoming.context("orders"), MessageBatch.create(List.of(Message.create("first"),
                                                                        Message.create("poison"),
                                                                        Message.create("deferred"))));

        assertThat(handled, is(List.of("first", "poison", "deferred")));
        assertThat(failedSubsetSettledBeforeDeferred.get(), is(true));
        assertThat(outgoing.messages().size(), is(1));
        DeadLetterMessage<?> deadLetter = (DeadLetterMessage<?>) outgoing.messages().getFirst();
        assertThat(deadLetter.entity(), is("poison"));
        assertThat(deadLetter.failureMessage(), is("application failed"));
    }

    @Test
    void testDeferredSubsetRetainsItsPriorFailedAttemptCount() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger dispatches = new AtomicInteger();
        IllegalStateException processingFailure = new IllegalStateException("failed");
        ConsumerRegistration source = batchRegistration("orders", batch -> {
            int dispatch = dispatches.incrementAndGet();
            if (dispatch == 1) {
                throw BatchDeliveryExceptionSupport.indeterminate("First attempt", batch, processingFailure);
            }
            if (dispatch == 2) {
                throw new BatchDeliveryException(
                        "Retry partially deferred",
                        processingFailure,
                        batch,
                        List.of(BatchItemOutcome.indeterminate(0, processingFailure),
                                BatchItemOutcome.notAttempted(1)));
            }
            throw BatchDeliveryExceptionSupport.indeterminate("Deferred attempt", batch, processingFailure);
        });
        ChannelRegistry registry = registry(List.of(source),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT0.001S
                                                max-attempts: 2
                                              on-exhausted: DROP
                                    """),
                            List.of(incoming));
        start(registry);

        deliver(incoming.context("orders"), MessageBatch.create(List.of(Message.create("first"),
                                                                        Message.create("deferred"))));

        assertThat(dispatches.get(), is(3));
    }

    @Test
    void testMultipleSubsetRetriesPreserveRootIdentityAndOutcomeIndexes() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger dispatches = new AtomicInteger();
        List<MessageBatch<?>> seen = new CopyOnWriteArrayList<>();
        IllegalStateException firstFailure = new IllegalStateException("first failed");
        IllegalStateException thirdFailure = new IllegalStateException("third failed");
        ConsumerRegistration source = batchRegistration("orders", batch -> {
            seen.add(batch);
            switch (dispatches.incrementAndGet()) {
            case 1 -> throw new BatchDeliveryException(
                    "Initial partial failure",
                    firstFailure,
                    batch,
                    List.of(BatchItemOutcome.succeeded(0),
                            BatchItemOutcome.indeterminate(1, firstFailure),
                            BatchItemOutcome.notAttempted(2),
                            BatchItemOutcome.indeterminate(3, thirdFailure)));
            case 2 -> throw new BatchDeliveryException(
                    "Retry retained only third",
                    thirdFailure,
                    batch,
                    List.of(BatchItemOutcome.succeeded(0),
                            BatchItemOutcome.indeterminate(1, thirdFailure)));
            default -> {
            }
            }
        });
        ChannelRegistry registry = registry(List.of(source),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                delay: PT0.001S
                                                max-attempts: 3
                                    """),
                            List.of(incoming));
        start(registry);
        MessageBatch<String> root = MessageBatch.<String>builder()
                .id("root-delivery")
                .messages(List.of(Message.create("zero"),
                                  Message.create("one"),
                                  Message.create("two"),
                                  Message.create("three")))
                .build();

        deliver(incoming.context("orders"), root);

        assertThat(seen.stream().map(MessageBatch::id).toList(),
                   is(List.of("root-delivery", "root-delivery", "root-delivery", "root-delivery")));
        assertThat(seen.stream().allMatch(batch -> batch == root || batch.isRetainedSubsetOf(root)), is(true));
        assertThat(seen.stream().map(MessageBatch::payloads).toList(),
                   is(List.of(List.of("zero", "one", "two", "three"),
                              List.of("one", "three"),
                              List.of("three"),
                              List.of("two"))));
    }

    @Test
    void testPartialTerminalDropProcessesDeferredSubset() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        List<String> handled = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                String entity = (String) message.entity();
                                handled.add(entity);
                                if (entity.equals("poison")) {
                                    throw new IllegalStateException("failed");
                                }
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DROP
                                    """),
                            List.of(incoming));
        start(registry);

        deliver(incoming.context("orders"), MessageBatch.create(List.of(Message.create("poison"),
                                                                        Message.create("deferred"))));

        assertThat(handled, is(List.of("poison", "deferred")));
    }

    @Test
    void testAllNotAttemptedBatchConsumesPolicyAttempt() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger attempts = new AtomicInteger();
        ConsumerRegistration source = batchRegistration("orders", batch -> {
            attempts.incrementAndGet();
            throw BatchDeliveryExceptionSupport.notAttempted(
                    "Source deferred every item",
                    batch,
                    new MessagingException("not admitted"));
        });
        ChannelRegistry registry = registry(List.of(source),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DROP
                                    """),
                            List.of(incoming));
        start(registry);

        deliver(incoming.context("orders"), MessageBatch.create(List.of(Message.create("one"),
                                                                        Message.create("two"))));

        assertThat(attempts.get(), is(1));
    }

    @Test
    void testTerminalFailStopsBeforeDeferredSubsetAndReportsOriginalBatch() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        IllegalStateException processingFailure = new IllegalStateException("poison failed");
        List<String> handled = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                String entity = (String) message.entity();
                                handled.add(entity);
                                if (entity.equals("poison")) {
                                    throw processingFailure;
                                }
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                    """),
                            List.of(incoming));
        start(registry);
        MessageBatch<String> root = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("poison"),
                                                                 Message.create("deferred")));

        BatchDeliveryException failure = assertThrows(BatchDeliveryException.class,
                                                       () -> deliver(incoming.context("orders"), root));

        assertThat(failure.batch(), sameInstance(root));
        assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.SUCCEEDED,
                              BatchItemStatus.INDETERMINATE,
                              BatchItemStatus.NOT_ATTEMPTED)));
        assertThat(failure.outcome(1).failure().orElseThrow(), sameInstance(processingFailure));
        assertThat(handled, is(List.of("first", "poison")));
    }

    @Test
    void testTryStartTerminalFailureReportsExactRetainedBatch() {
        TestIncomingConnector incoming = new TestIncomingConnector();
        IllegalStateException processingFailure = new IllegalStateException("poison failed");
        List<String> handled = new CopyOnWriteArrayList<>();
        ChannelRegistry registry = registry(List.of(registration("orders", message -> {
                                String entity = (String) message.entity();
                                handled.add(entity);
                                if (entity.equals("poison")) {
                                    throw processingFailure;
                                }
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                    """),
                            List.of(incoming));
        start(registry);
        MessageBatch<String> root = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("poison"),
                                                                 Message.create("deferred")));

        BatchDeliveryException failure;
        try (ConnectorDeliveryReservation reservation = incoming.context("orders").reserveDelivery();
             ConnectorDelivery delivery = reservation.tryStart(root).orElseThrow()) {
            failure = assertThrows(BatchDeliveryException.class,
                                   () -> delivery.await(Duration.ofSeconds(2)));
        }

        assertThat(failure.batch(), sameInstance(root));
        assertThat(failure.outcomes().stream().map(BatchItemOutcome::index).toList(), is(List.of(0, 1, 2)));
        assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.SUCCEEDED,
                              BatchItemStatus.INDETERMINATE,
                              BatchItemStatus.NOT_ATTEMPTED)));
        assertThat(failure.outcome(1).failure().orElseThrow(), sameInstance(processingFailure));
        assertThat(handled, is(List.of("first", "poison")));
    }

    @Test
    void testUnlimitedDropIsRejectedBeforeConnectorsAreCreated() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();

        assertThrows(RuntimeException.class,
                     () -> registry(List.of(),
                                               yaml("""
                                                       helidon:
                                                         messaging:
                                                           incoming:
                                                             orders:
                                                               connector: test-in
                                                               failure:
                                                                 on-exhausted: DROP
                                                           outgoing:
                                                             audit:
                                                               connector: test-out
                                                       """),
                                               List.of(incoming, outgoing)));

        assertThat(incoming.createdCount(), is(0));
        assertThat(outgoing.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testConflictingDeclaredFailurePoliciesAreRejectedAfterConfigMerge() {
        TestIncomingConnector incoming = new TestIncomingConnector();
        FailurePolicy firstPolicy = FailurePolicy.builder()
                .maxAttempts(2)
                .onExhausted(FailureDisposition.DROP)
                .build();
        FailurePolicy secondPolicy = FailurePolicy.builder()
                .maxAttempts(3)
                .onExhausted(FailureDisposition.DROP)
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(
                        List.of(registration("first-handler", "orders", firstPolicy, ignored -> { }),
                                registration("second-handler", "orders", secondPolicy, ignored -> { })),
                        yaml("""
                                helidon:
                                  messaging:
                                    incoming:
                                      orders:
                                        connector: test-in
                                """),
                        List.of(incoming)));

        assertThat(failure.getMessage(), containsString("Incoming channel orders"));
        assertThat(failure.getMessage(), containsString("first-handler"));
        assertThat(failure.getMessage(), containsString("second-handler"));
        assertThat(incoming.createdCount(), is(0));
    }

    @Test
    void testConfigResolvesDeclaredPolicyConflictAndClearsInheritedDeadLetterChannels() {
        TestIncomingConnector incoming = new TestIncomingConnector();
        FailurePolicy firstPolicy = FailurePolicy.builder()
                .retryDelay(Duration.ofMillis(7))
                .maxAttempts(2)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetterChannel("first-dlq")
                .build();
        FailurePolicy secondPolicy = FailurePolicy.builder()
                .retryDelay(Duration.ofMillis(7))
                .maxAttempts(3)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetterChannel("second-dlq")
                .build();

        ChannelRegistry registry = registry(
                List.of(registration("first-handler", "orders", firstPolicy, ignored -> { }),
                        registration("second-handler", "orders", secondPolicy, ignored -> { })),
                yaml("""
                        helidon:
                          messaging:
                            incoming:
                              orders:
                                connector: test-in
                                failure:
                                  retry:
                                    max-attempts: 1
                                  on-exhausted: DROP
                        """),
                List.of(incoming));
        try {
            assertThat(incoming.createdCount(), is(1));
        } finally {
            registry.close();
        }
    }

    @Test
    void testDeadLetterRouteFailureIsNotRecursivelyHandled() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        IllegalStateException routeFailure = new IllegalStateException("sink failed");
        IllegalStateException processingFailure = new IllegalStateException("handler failed");
        TestOutgoingConnector outgoing = new TestOutgoingConnector(routeFailure);
        ChannelRegistry registry = registry(List.of(registration("orders", ignored -> {
                                throw processingFailure;
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                        outgoing:
                                          orders-dlq:
                                            connector: test-out
                                    """),
                            List.of(incoming, outgoing));
        start(registry);

        BatchDeliveryException result = assertThrows(
                BatchDeliveryException.class,
                () -> deliver(incoming.context("orders"),
                              MessageBatch.create(List.of(Message.create("order-1")))));

        assertThat(result.getCause(), sameInstance(routeFailure));
        assertThat(result.getSuppressed().length, is(1));
        assertThat(result.getSuppressed()[0], instanceOf(BatchDeliveryException.class));
        assertThat(outgoing.sendCount(), is(1));
    }

    @Test
    void testPartialDeadLetterRouteFailureMapsOutcomesToOriginalBatch() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        IllegalStateException routeFailure = new IllegalStateException("DLQ consumer failed");
        IllegalStateException processingFailure = new IllegalStateException("handler failed");
        List<String> routed = new CopyOnWriteArrayList<>();
        ConsumerRegistration deadLetterConsumer = registration("orders-dlq", message -> {
            String entity = (String) message.entity();
            routed.add(entity);
            if (entity.equals("second")) {
                throw routeFailure;
            }
        });
        ConsumerRegistration source = batchRegistration(
                "orders",
                batch -> {
                    throw BatchDeliveryExceptionSupport.indeterminate("Source delivery", batch, processingFailure);
                });
        ChannelRegistry registry = registry(List.of(source, deadLetterConsumer),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                    """),
                            List.of(incoming));
        start(registry);
        MessageBatch<String> policyBatch = MessageBatch.create(List.of(Message.create("first"),
                                                                       Message.create("second"),
                                                                       Message.create("third")));

        BatchDeliveryException failure = assertThrows(BatchDeliveryException.class,
                                                       () -> deliver(incoming.context("orders"), policyBatch));

        assertThat(failure.batch(), sameInstance(policyBatch));
        assertThat(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.SUCCEEDED,
                              BatchItemStatus.INDETERMINATE,
                              BatchItemStatus.NOT_ATTEMPTED)));
        assertThat(failure.outcome(1).failure().orElseThrow(), sameInstance(routeFailure));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0], instanceOf(BatchDeliveryException.class));
        assertThat(routed, is(List.of("first", "second", "second")));
    }

    @Test
    void testCancellationStopsUnlimitedRetryAfterHandlerClearsInterrupt() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        ChannelRegistry registry = registry(List.of(registration("orders", ignored -> {
                                attempts.incrementAndGet();
                                entered.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                } catch (InterruptedException e) {
                                    Thread.interrupted();
                                    throw new IllegalStateException("handler cleared cancellation", e);
                                }
                            })),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                    """),
                            List.of(incoming));
        start(registry);

        try (ConnectorDeliveryReservation reservation = incoming.context("orders").reserveDelivery();
             ConnectorDelivery delivery = reservation.start(MessageBatch.create(Message.create("order-1")))) {
            assertThat(entered.await(1, TimeUnit.SECONDS), is(true));
            delivery.cancel();
            MessagingRejectedException cancelled = assertThrows(
                    MessagingRejectedException.class,
                    () -> delivery.await(Duration.ofSeconds(1)));
            assertThat(cancelled.reason(), is(MessagingRejectedException.Reason.CANCELLED));
            assertThat(attempts.get(), is(1));
        }
    }

    @Test
    void testCancellationDuringPartialDeadLetterRouteDoesNotRetryUnresolvedSubset() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        IllegalStateException processingFailure = new IllegalStateException("source failed");
        CountDownLatch secondEntered = new CountDownLatch(1);
        List<String> routed = new CopyOnWriteArrayList<>();
        ConsumerRegistration source = batchRegistration("orders", batch -> {
            throw BatchDeliveryExceptionSupport.indeterminate("Source delivery", batch, processingFailure);
        });
        ConsumerRegistration deadLetter = registration("orders-dlq", message -> {
            String entity = (String) message.entity();
            routed.add(entity);
            if (entity.equals("second")) {
                secondEntered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    throw new IllegalStateException("route cleared cancellation", e);
                }
            }
        });
        ChannelRegistry registry = registry(List.of(source, deadLetter),
                            yaml("""
                                    helidon:
                                      messaging:
                                        incoming:
                                          orders:
                                            connector: test-in
                                            failure:
                                              retry:
                                                max-attempts: 1
                                              on-exhausted: DEAD_LETTER
                                              dead-letter:
                                                channel: orders-dlq
                                    """),
                            List.of(incoming));
        start(registry);

        try (ConnectorDeliveryReservation reservation = incoming.context("orders").reserveDelivery();
             ConnectorDelivery delivery = reservation.start(
                     MessageBatch.create(List.of(Message.create("first"),
                                                 Message.create("second"),
                                                 Message.create("third"))))) {
            assertThat(secondEntered.await(1, TimeUnit.SECONDS), is(true));
            delivery.cancel();
            MessagingRejectedException cancelled = assertThrows(
                    MessagingRejectedException.class,
                    () -> delivery.await(Duration.ofSeconds(1)));
            assertThat(cancelled.reason(), is(MessagingRejectedException.Reason.CANCELLED));
            assertThat(routed, is(List.of("first", "second")));
        }
    }

    @Test
    void testUnknownRouteCreatesNoConnectors() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        first:
                                                          connector: test-in
                                                        second:
                                                          connector: test-in
                                                          failure:
                                                            retry:
                                                              max-attempts: 1
                                                            on-exhausted: DEAD_LETTER
                                                            dead-letter:
                                                              channel: missing
                                                      outgoing:
                                                        audit:
                                                          connector: test-out
                                                  """),
                                          List.of(incoming, outgoing)));

        assertThat(failure.getMessage(), containsString("Unknown dead-letter channel missing"));
        assertThat(incoming.createdCount(), is(0));
        assertThat(outgoing.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testOutputlessAndSelfRoutesAreRejectedBeforeSourceStarts() throws InterruptedException {
        TestIncomingConnector outputless = new TestIncomingConnector();
        IllegalArgumentException outputlessFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          connector: test-in
                                                          failure:
                                                            retry:
                                                              max-attempts: 1
                                                            on-exhausted: DEAD_LETTER
                                                            dead-letter:
                                                              channel: empty
                                                        empty:
                                                          connector: test-in
                                                  """),
                                          List.of(outputless)));
        assertThat(outputlessFailure.getMessage(), containsString("has no outputs"));
        assertThat(outputless.createdCount(), is(0));
        assertThat(outputless.awaitAnyStart(), is(false));

        TestIncomingConnector self = new TestIncomingConnector();
        IllegalArgumentException selfFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(registration("orders", ignored -> { })),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          connector: test-in
                                                          failure:
                                                            retry:
                                                              max-attempts: 1
                                                            on-exhausted: DEAD_LETTER
                                                            dead-letter:
                                                              channel: orders
                                                  """),
                                          List.of(self)));
        assertThat(selfFailure.getMessage(), containsString("must not reference itself"));
        assertThat(self.createdCount(), is(0));
        assertThat(self.awaitAnyStart(), is(false));
    }

    @Test
    void testCyclicRoutesAreRejectedBeforeSourceStarts() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(
                        List.of(registration("a", ignored -> { }),
                                registration("b", ignored -> { }),
                                registration("c", ignored -> { })),
                        yaml("""
                                helidon:
                                  messaging:
                                    incoming:
                                      a:
                                        connector: test-in
                                        failure:
                                          retry:
                                            max-attempts: 1
                                          on-exhausted: DEAD_LETTER
                                          dead-letter:
                                            channel: b
                                      b:
                                        connector: test-in
                                        failure:
                                          retry:
                                            max-attempts: 1
                                          on-exhausted: DEAD_LETTER
                                          dead-letter:
                                            channel: c
                                      c:
                                        connector: test-in
                                        failure:
                                          retry:
                                            max-attempts: 1
                                          on-exhausted: DEAD_LETTER
                                          dead-letter:
                                            channel: a
                                """),
                        List.of(incoming)));

        assertThat(failure.getMessage(), containsString("Cyclic dead-letter channel route"));
        assertThat(failure.getMessage(), containsString("a"));
        assertThat(failure.getMessage(), containsString("b"));
        assertThat(failure.getMessage(), containsString("c"));
        assertThat(incoming.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testConfiguredConnectorWithoutProviderIsRejected() {
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        IllegalArgumentException incomingFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          connector: missing-in
                                                      outgoing:
                                                        audit:
                                                          connector: test-out
                                                  """),
                                          List.of(outgoing)));
        assertThat(incomingFailure.getMessage(), containsString("No connector provider of type missing-in"));
        assertThat(outgoing.createdCount(), is(0));

        IllegalArgumentException outgoingFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      outgoing:
                                                        orders:
                                                          connector: missing-out
                                                  """),
                                          List.of()));
        assertThat(outgoingFailure.getMessage(), containsString("No connector provider of type missing-out"));
    }

    @Test
    void testConfiguredIncomingChannelRequiresConnector() {
        TestIncomingConnector incoming = new TestIncomingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(registration("orders", ignored -> { })),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          destination: orders
                                                  """),
                                          List.of(incoming)));

        assertThat(failure.getMessage(),
                   containsString("Configured incoming channel orders must declare a non-blank connector"));
        assertThat(incoming.createdCount(), is(0));
    }

    @Test
    void testConfiguredOutgoingChannelRequiresConnector() {
        TestOutgoingConnector outgoing = new TestOutgoingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(registration("orders", ignored -> { })),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      outgoing:
                                                        orders:
                                                          destination: orders
                                                  """),
                                          List.of(outgoing)));

        assertThat(failure.getMessage(),
                   containsString("Configured outgoing channel orders must declare a non-blank connector"));
        assertThat(outgoing.createdCount(), is(0));
    }

    @Test
    void testUnsupportedProviderDirectionIsRejectedBeforeConnectorCreation() {
        TestOutgoingConnector outgoing = new TestOutgoingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(registration("orders", ignored -> { })),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          connector: test-out
                                                  """),
                                          List.of(outgoing)));

        assertThat(failure.getMessage(), containsString("does not support incoming channel orders"));
        assertThat(outgoing.createdCount(), is(0));
    }

    @Test
    void testDuplicateConnectorProviderTypeIsRejected() {
        TestOutgoingConnector first = new TestOutgoingConnector();
        TestOutgoingConnector second = new TestOutgoingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(), yaml("{}"), List.of(first, second)));

        assertThat(failure.getMessage(), containsString("Duplicate connector provider type test-out"));
        assertThat(first.createdCount(), is(0));
        assertThat(second.createdCount(), is(0));
    }

    @Test
    void testBlankConnectorProviderTypeIsRejected() {
        AtomicInteger configCreated = new AtomicInteger();
        ConnectorProvider provider = new ConnectorProvider() {
            @Override
            public String connectorType() {
                return " ";
            }

        };

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(), yaml("{}"), List.of(provider)));

        assertThat(failure.getMessage(), containsString("Connector provider type must not be blank"));
        assertThat(configCreated.get(), is(0));
    }

    @Test
    void testIncomingChannelWithoutOutputIsRejectedBeforeConnectorsAreCreated() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(),
                                          yaml("""
                                                  helidon:
                                                    messaging:
                                                      incoming:
                                                        orders:
                                                          connector: test-in
                                                      outgoing:
                                                        audit:
                                                          connector: test-out
                                                  """),
                                          List.of(incoming, outgoing)));

        assertThat(failure.getMessage(), containsString("Incoming channel orders has no outputs"));
        assertThat(incoming.createdCount(), is(0));
        assertThat(outgoing.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testGenericConsumerTypesAreValidatedBeforeDispatch() {
        AtomicInteger broadDispatches = new AtomicInteger();
        AtomicInteger keyedDispatches = new AtomicInteger();
        ConsumerRegistration broad = registration(
                "orders",
                Integer.class,
                new GenericType<Integer>() { },
                Message.class,
                new GenericType<Message<Integer>>() { },
                ignored -> broadDispatches.incrementAndGet());
        ConsumerRegistration keyed = registration(
                "orders",
                Integer.class,
                new GenericType<Integer>() { },
                TestKeyedMessage.class,
                new GenericType<TestKeyedMessage<String, Integer>>() { },
                ignored -> keyedDispatches.incrementAndGet());

        ChannelRegistry registry = registry(List.of(broad, keyed), yaml("{}"), List.of());
        IllegalArgumentException dispatchFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry.emit("orders", Message.create(1)));
        assertThat(dispatchFailure.getMessage(), containsString(TestKeyedMessage.class.getName()));
        assertThat(broadDispatches.get(), is(0));
        assertThat(keyedDispatches.get(), is(0));

        ConsumerRegistration conflictingKeyed = registration(
                "orders",
                Integer.class,
                new GenericType<Integer>() { },
                TestKeyedMessage.class,
                new GenericType<TestKeyedMessage<Long, Integer>>() { });
        IllegalArgumentException envelopeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(keyed, conflictingKeyed), yaml("{}"), List.of()));
        assertThat(envelopeFailure.getMessage(), containsString("conflicting message envelope types"));

        ConsumerRegistration conflictingSubtype = registration(
                "orders",
                Integer.class,
                new GenericType<Integer>() { },
                TestKeyedMessageSubtype.class,
                new GenericType<TestKeyedMessageSubtype<Long, Integer>>() { });
        assertThrows(IllegalArgumentException.class,
                     () -> registry(List.of(keyed, conflictingSubtype),
                                               yaml("{}"),
                                               List.of()));

        ConsumerRegistration stringList = registration(
                "lists",
                List.class,
                new GenericType<List<String>>() { },
                Message.class,
                new GenericType<Message<List<String>>>() { });
        ConsumerRegistration integerList = registration(
                "lists",
                List.class,
                new GenericType<List<Integer>>() { },
                Message.class,
                new GenericType<Message<List<Integer>>>() { });
        IllegalArgumentException payloadFailure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(stringList, integerList), yaml("{}"), List.of()));
        assertThat(payloadFailure.getMessage(), containsString("conflicting payload types"));
        assertThat(payloadFailure.getMessage(), containsString("java.util.List<java.lang.String>"));
        assertThat(payloadFailure.getMessage(), containsString("java.util.List<java.lang.Integer>"));
    }

    @Test
    void testDeadLetterTargetRejectsIncompatibleConsumerBeforeConnectorsAreCreated() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        ConsumerRegistration source = registration("orders", ignored -> { });
        ConsumerRegistration incompatibleTarget = registration(
                "orders-dlq",
                String.class,
                new GenericType<String>() { },
                TestSpecialMessage.class,
                new GenericType<TestSpecialMessage<String>>() { });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(
                        List.of(source, incompatibleTarget),
                        yaml("""
                                helidon:
                                  messaging:
                                    incoming:
                                      orders:
                                        connector: test-in
                                        failure:
                                          retry:
                                            max-attempts: 1
                                          on-exhausted: DEAD_LETTER
                                          dead-letter:
                                            channel: orders-dlq
                                    outgoing:
                                      audit:
                                        connector: test-out
                                """),
                        List.of(incoming, outgoing)));

        assertThat(failure.getMessage(), containsString("cannot accept"));
        assertThat(failure.getMessage(), containsString(DeadLetterMessage.class.getName()));
        assertThat(incoming.createdCount(), is(0));
        assertThat(outgoing.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testDeadLetterTargetRejectsIncompatiblePayloadBeforeConnectorsAreCreated() throws InterruptedException {
        TestIncomingConnector incoming = new TestIncomingConnector();
        TestOutgoingConnector outgoing = new TestOutgoingConnector();
        ConsumerRegistration source = registration("orders", ignored -> { });
        ConsumerRegistration incompatibleTarget = registration(
                "orders-dlq",
                Integer.class,
                new GenericType<Integer>() { },
                DeadLetterMessage.class,
                new GenericType<DeadLetterMessage<Integer>>() { });

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(
                        List.of(source, incompatibleTarget),
                        yaml("""
                                helidon:
                                  messaging:
                                    incoming:
                                      orders:
                                        connector: test-in
                                        failure:
                                          retry:
                                            max-attempts: 1
                                          on-exhausted: DEAD_LETTER
                                          dead-letter:
                                            channel: orders-dlq
                                    outgoing:
                                      audit:
                                        connector: test-out
                                """),
                        List.of(incoming, outgoing)));

        assertThat(failure.getMessage(), containsString("Dead-letter channel orders-dlq"));
        assertThat(failure.getMessage(), containsString("payload type java.lang.Integer"));
        assertThat(failure.getMessage(), containsString("incoming channel orders has payload type java.lang.String"));
        assertThat(incoming.createdCount(), is(0));
        assertThat(outgoing.createdCount(), is(0));
        assertThat(incoming.awaitAnyStart(), is(false));
    }

    @Test
    void testRawProducerEnvelopeDoesNotSatisfyParameterizedConsumer() {
        ConsumerRegistration target = registration(
                "orders",
                Integer.class,
                new GenericType<Integer>() { },
                TestKeyedMessage.class,
                new GenericType<TestKeyedMessage<Long, Integer>>() { });
        EmitterRegistration rawProducer = emitterRegistration(
                "orders",
                "publisher#orders",
                new GenericType<Integer>() { },
                GenericType.create(TestKeyedMessage.class));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry(List.of(target),
                                          List.of(rawProducer),
                                          yaml("{}"),
                                          List.of()));

        assertThat(failure.getMessage(), containsString("produces envelope type"));
        assertThat(failure.getMessage(), containsString("cannot accept"));
        assertThat(failure.getMessage(), containsString("TestKeyedMessage<java.lang.Long, java.lang.Integer>"));
    }

    private static ChannelRegistry registry(List<ConsumerRegistration> consumerRegistrations,
                                            Config config,
                                            List<ConnectorProvider> connectorProviders) {
        return registry(consumerRegistrations, List.of(), config, connectorProviders);
    }

    private static ChannelRegistry registry(List<ConsumerRegistration> consumerRegistrations,
                                            List<EmitterRegistration> emitterRegistrations,
                                            Config config,
                                            List<ConnectorProvider> connectorProviders) {
        return new ChannelRegistry(consumerRegistrations,
                                   emitterRegistrations,
                                   config,
                                   connectorProviders,
                                   new MessagingLifecycleGuard());
    }

    private static MessagingRejectedException awaitConfiguredTimeout(IncomingConnectorContext context,
                                                                      Duration configuredTimeout,
                                                                      Duration testTimeout) {
        long started = System.nanoTime();
        while (true) {
            if (System.nanoTime() - started >= testTimeout.toNanos()) {
                fail("Configured admission timeout did not expire within " + testTimeout);
            }
            try {
                var attempt = context.tryReserveDelivery();
                if (attempt.isPresent()) {
                    try (ConnectorDeliveryReservation ignored = attempt.orElseThrow()) {
                        fail("Non-blocking reservation succeeded while capacity was held");
                    }
                }
            } catch (MessagingRejectedException e) {
                assertThat("Reservation timed out before the configured admission budget elapsed",
                           System.nanoTime() - started,
                           greaterThanOrEqualTo(configuredTimeout.toNanos()));
                return e;
            }
        }
    }

    private static MessagingRejectedException awaitTimeoutProbe(FutureTask<MessagingRejectedException> timeoutProbe) {
        Thread.ofVirtual().name("messaging-admission-timeout-probe").start(timeoutProbe);
        try {
            return timeoutProbe.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timeoutProbe.cancel(true);
            throw new AssertionError("Admission timeout probe did not complete within 10 seconds", e);
        } catch (InterruptedException e) {
            timeoutProbe.cancel(true);
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for admission timeout probe", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Admission timeout probe failed", cause);
        }
    }

    private static Config yaml(String yaml) {
        return Config.just(yaml, MediaTypes.APPLICATION_YAML);
    }

    private void start(ChannelRegistry registry) {
        startedRegistries.add(registry);
        registry.start();
    }

    private static void deliver(IncomingConnectorContext context,
                                MessageBatch<?> batch) {
        try (ConnectorDeliveryReservation reservation = context.reserveDelivery();
             ConnectorDelivery delivery = reservation.start(batch)) {
            delivery.await();
        }
    }

    private static void deliverFailed(IncomingConnectorContext context,
                                      MessageBatch<?> batch,
                                      RuntimeException failure) {
        try (ConnectorDeliveryReservation reservation = context.reserveDelivery();
             ConnectorDelivery delivery = reservation.startFailed(batch, failure)) {
            delivery.await();
        }
    }

    private static BatchDeliveryException mixedPreDispatchFailure(MessageBatch<?> batch,
                                                                  RuntimeException mappingFailure) {
        return new BatchDeliveryException(
                "Partial mapping failure",
                mappingFailure,
                batch,
                List.of(BatchItemOutcome.notAttempted(0),
                        BatchItemOutcome.failed(1, mappingFailure),
                        BatchItemOutcome.notAttempted(2)));
    }

    private static Message<String> customMessage(String entity) {
        return new Message<>() {
            @Override
            public String entity() {
                return entity;
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };
    }

    private static ConsumerRegistration registration(String channel, Consumer<Message<?>> consumer) {
        return registration(channel,
                            String.class,
                            new GenericType<String>() { },
                            Message.class,
                            new GenericType<Message<String>>() { },
                            consumer);
    }

    private static ConsumerRegistration registration(String handlerId,
                                                     String channel,
                                                     FailurePolicy failurePolicy,
                                                     Consumer<Message<?>> consumer) {
        return registration(handlerId,
                            channel,
                            String.class,
                            new GenericType<String>() { },
                            Message.class,
                            new GenericType<Message<String>>() { },
                            failurePolicy,
                            consumer);
    }

    private static ConsumerRegistration batchRegistration(String channel,
                                                          Consumer<MessageBatch<?>> consumer) {
        return new ConsumerRegistration() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public Class<?> payloadType() {
                return String.class;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return new GenericType<String>() { };
            }

            @Override
            public Class<?> envelopeType() {
                return Message.class;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return new GenericType<Message<String>>() { };
            }

            @Override
            public void dispatch(MessageBatch<?> batch) {
                consumer.accept(batch);
            }
        };
    }

    private static ProcessorRegistration passThroughProcessor(String incoming,
                                                              String outgoing,
                                                              AtomicInteger attempts) {
        return new ProcessorRegistration() {
            @Override
            public String handlerId() {
                return incoming + "->" + outgoing;
            }

            @Override
            public String channel() {
                return incoming;
            }

            @Override
            public Class<?> payloadType() {
                return String.class;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return new GenericType<String>() { };
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return new GenericType<Message<String>>() { };
            }

            @Override
            public String outgoingChannel() {
                return outgoing;
            }

            @Override
            public GenericType<?> outgoingPayloadGenericType() {
                return new GenericType<String>() { };
            }

            @Override
            public GenericType<?> outgoingEnvelopeGenericType() {
                return new GenericType<Message<String>>() { };
            }

            @Override
            public MessageBatch<?> process(MessageBatch<?> batch) {
                attempts.incrementAndGet();
                return batch;
            }
        };
    }

    private static ConsumerRegistration registration(String channel,
                                                     Class<?> payloadType,
                                                     GenericType<?> payloadGenericType,
                                                     Class<?> envelopeType,
                                                     GenericType<?> envelopeGenericType) {
        return registration(channel,
                            payloadType,
                            payloadGenericType,
                            envelopeType,
                            envelopeGenericType,
                            ignored -> { });
    }

    private static ConsumerRegistration registration(String channel,
                                                     Class<?> payloadType,
                                                     GenericType<?> payloadGenericType,
                                                     Class<?> envelopeType,
                                                     GenericType<?> envelopeGenericType,
                                                     Consumer<Message<?>> consumer) {
        return registration(null,
                            channel,
                            payloadType,
                            payloadGenericType,
                            envelopeType,
                            envelopeGenericType,
                            null,
                            consumer);
    }

    private static ConsumerRegistration registration(String handlerId,
                                                     String channel,
                                                     Class<?> payloadType,
                                                     GenericType<?> payloadGenericType,
                                                     Class<?> envelopeType,
                                                     GenericType<?> envelopeGenericType,
                                                     FailurePolicy failurePolicy,
                                                     Consumer<Message<?>> consumer) {
        return new ConsumerRegistration() {
            @Override
            public String handlerId() {
                return handlerId == null ? ConsumerRegistration.super.handlerId() : handlerId;
            }

            @Override
            public String channel() {
                return channel;
            }

            @Override
            public Class<?> payloadType() {
                return payloadType;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return payloadGenericType;
            }

            @Override
            public Class<?> envelopeType() {
                return envelopeType;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return envelopeGenericType;
            }

            @Override
            public java.util.Optional<FailurePolicy> declaredFailurePolicy() {
                return java.util.Optional.ofNullable(failurePolicy);
            }

            @Override
            public void dispatch(MessageBatch<?> batch) {
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        consumer.accept(batch.get(i));
                    } catch (RuntimeException e) {
                        throw BatchDeliveryExceptionSupport.sequential("Test consumer", batch, i, e);
                    }
                }
            }
        };
    }

    private static EmitterRegistration emitterRegistration(String channel,
                                                           String producerId,
                                                           GenericType<?> payloadType,
                                                           GenericType<?> envelopeType) {
        return new EmitterRegistration() {
            @Override
            public String channel() {
                return channel;
            }

            @Override
            public String producerId() {
                return producerId;
            }

            @Override
            public GenericType<?> payloadGenericType() {
                return payloadType;
            }

            @Override
            public GenericType<?> envelopeGenericType() {
                return envelopeType;
            }
        };
    }

    private interface TestKeyedMessage<K, V> extends Message<V> {
    }

    private interface TestKeyedMessageSubtype<K, V> extends TestKeyedMessage<K, V> {
    }

    private interface TestSpecialMessage<T> extends Message<T> {
    }

    public record TestConnectorConfig(ConnectorDirection direction,
                                      String channelName,
                                      String connector,
                                      Map<String, String> properties,
                                      Config config) implements ConnectorConfig {
        private static TestConnectorConfig from(Config config) {
            return new TestConnectorConfig(
                    ConnectorDirection.valueOf(config.get("direction").asString().orElseThrow()),
                    config.get(ConnectorConfig.CHANNEL_NAME_ATTRIBUTE).asString().orElseThrow(),
                    config.get(ConnectorConfig.CONNECTOR_ATTRIBUTE).asString().orElseThrow(),
                    Map.copyOf(config.detach().asMap().orElse(Map.of())),
                    config.detach());
        }
    }

    static final class TestIncomingConnector implements IncomingConnectorProvider {
        private final String connectorType;
        private final Map<String, IncomingConnectorContext> contexts = new ConcurrentHashMap<>();
        private final Map<String, TestConnectorConfig> configs = new ConcurrentHashMap<>();
        private final AtomicInteger configCreated = new AtomicInteger();
        private final AtomicInteger created = new AtomicInteger();
        private final CountDownLatch anyStart = new CountDownLatch(1);

        private TestIncomingConnector() {
            this("test-in");
        }

        private TestIncomingConnector(String connectorType) {
            this.connectorType = connectorType;
        }

        @Override
        public String connectorType() {
            return connectorType;
        }

        @Override
        public IncomingConnector createIncomingConnector(Config config) {
            configCreated.incrementAndGet();
            TestConnectorConfig connectorConfig = TestConnectorConfig.from(config);
            created.incrementAndGet();
            configs.put(connectorConfig.channelName(), connectorConfig);
            return new IncomingConnector() {
                private final CountDownLatch stopped = new CountDownLatch(1);
                private final AtomicBoolean closed = new AtomicBoolean();

                @Override
                public void run(IncomingConnectorContext context) {
                    contexts.put(connectorConfig.channelName(), context);
                    anyStart.countDown();
                    if (!context.awaitRunning()) {
                        return;
                    }
                    await(stopped, Duration.ofDays(1), "stop");
                }

                @Override
                public void drain() {
                    stopped.countDown();
                }

                @Override
                public void forceClose() {
                    close();
                }

                @Override
                public void close() {
                    if (closed.compareAndSet(false, true)) {
                        stopped.countDown();
                    }
                }
            };
        }

        private static void await(CountDownLatch latch, Duration timeout, String operation) {
            try {
                if (!latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                    throw new MessagingException("Test incoming connector " + operation + " timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MessagingException("Test incoming connector " + operation + " was interrupted", e);
            }
        }

        private IncomingConnectorContext context(String channel) {
            IncomingConnectorContext context = contexts.get(channel);
            if (context == null) {
                throw new AssertionError("Connector context is not available; start the registry first");
            }
            return context;
        }

        private TestConnectorConfig config(String channel) {
            return configs.get(channel);
        }

        private int createdCount() {
            return created.get();
        }

        private int configCreatedCount() {
            return configCreated.get();
        }

        private boolean awaitAnyStart() throws InterruptedException {
            return anyStart.await(100, TimeUnit.MILLISECONDS);
        }
    }

    static final class TestOutgoingConnector implements OutgoingConnectorProvider {
        private final List<Message<?>> messages = new CopyOnWriteArrayList<>();
        private final AtomicInteger configCreated = new AtomicInteger();
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger sends = new AtomicInteger();
        private final RuntimeException failure;
        private final int maxTextHeaderLength;

        private TestOutgoingConnector() {
            this(null, Integer.MAX_VALUE);
        }

        private TestOutgoingConnector(RuntimeException failure) {
            this(failure, Integer.MAX_VALUE);
        }

        private TestOutgoingConnector(int maxTextHeaderLength) {
            this(null, maxTextHeaderLength);
        }

        private TestOutgoingConnector(RuntimeException failure, int maxTextHeaderLength) {
            this.failure = failure;
            this.maxTextHeaderLength = maxTextHeaderLength;
        }

        @Override
        public String connectorType() {
            return "test-out";
        }

        @Override
        public OutgoingConnector createOutgoingConnector(Config config) {
            configCreated.incrementAndGet();
            TestConnectorConfig.from(config);
            created.incrementAndGet();
            return new OutgoingConnector() {
                @Override
                public void start() {
                }

                @Override
                public void sendBatch(MessageBatch<?> batch) {
                    sends.addAndGet(batch.size());
                    if (failure != null) {
                        throw failure;
                    }
                    for (Message<?> message : batch) {
                        for (MessageHeader header : message.headers()) {
                            if (header.value() instanceof HeaderValue.TextValue textValue
                                    && textValue.value().length() > maxTextHeaderLength) {
                                throw new MessagingException("Test connector header exceeds transport limit");
                            }
                        }
                    }
                    messages.addAll(batch.messages());
                }

                @Override
                public void forceClose() {
                }

                @Override
                public void close() {
                }
            };
        }

        private List<Message<?>> messages() {
            return List.copyOf(messages);
        }

        private int sendCount() {
            return sends.get();
        }

        private int createdCount() {
            return created.get();
        }

        private int configCreatedCount() {
            return configCreated.get();
        }
    }
}
