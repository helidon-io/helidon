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

package io.helidon.declarative.tests.messaging;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.messaging.DeadLetterMessage;
import io.helidon.messaging.Emitter;
import io.helidon.messaging.FailureDisposition;
import io.helidon.messaging.MessageHeaderValue;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessageHeaders;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.spi.IncomingConnector;
import io.helidon.messaging.spi.IncomingConnectorContext;
import io.helidon.messaging.spi.IncomingConnectorProvider;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

class ChannelMessagingTypes {
    static final String CHANNEL_ONE = "channel-one";
    static final String CHANNEL_TWO = "channel-two";
    static final String CUSTOM_MESSAGE_CHANNEL = "custom-message-channel";
    static final String MULTI_HOP_MESSAGE_CHANNEL = "multi-hop-message-channel";
    static final String FORWARDING_INPUT_CHANNEL = "forwarding-input-channel";
    static final String FORWARDING_OUTPUT_CHANNEL = "forwarding-output-channel";
    static final String PAYLOAD_PROCESSOR_INPUT_CHANNEL = "payload-processor-input-channel";
    static final String PAYLOAD_PROCESSOR_OUTPUT_CHANNEL = "payload-processor-output-channel";
    static final String ARRAY_PROCESSOR_INPUT_CHANNEL = "array-processor-input-channel";
    static final String ARRAY_PROCESSOR_OUTPUT_CHANNEL = "array-processor-output-channel";
    static final String REQUIRED_HEADER_CHANNEL = "required-header-channel";
    static final String OPTIONAL_HEADER_CHANNEL = "optional-header-channel";
    static final String TYPED_HEADER_CHANNEL = "typed-header-channel";
    static final String PER_LOOKUP_INTERCEPTED_CHANNEL = "per-lookup-intercepted-channel";
    static final String FAILING_CHANNEL = "failing-channel";
    static final String CHECKED_FAILING_CHANNEL = "checked-failing-channel";
    static final String ANNOTATED_FAILURE_CHANNEL = "annotated-failure-channel";
    static final String ANNOTATED_FAILURE_DLQ_CHANNEL = "annotated-failure-dlq-channel";
    static final String TEST_CONNECTOR = "test";
    static final String SHUTDOWN_CHANNEL = "shutdown-channel";
    static final String SHUTDOWN_CONNECTOR = "shutdown-test";
    static final String SHUTDOWN_SINGLETON_CHANNEL = "shutdown-singleton-channel";

    private ChannelMessagingTypes() {
    }

    @Service.Singleton
    static class Producer {
        @Service.Named(CHANNEL_ONE)
        @Service.Inject
        Emitter<String> channelOne;

        @Service.Named(CHANNEL_TWO)
        @Service.Inject
        Emitter<String> channelTwo;

        @Service.Named(FAILING_CHANNEL)
        @Service.Inject
        Emitter<String> failingChannel;

        @Service.Named(CHECKED_FAILING_CHANNEL)
        @Service.Inject
        Emitter<String> checkedFailingChannel;

        void emitChannelOne(String entity) {
            channelOne.emit(Message.builder(entity)
                                    .header("key", "value")
                                    .build());
        }

        void emitChannelOneBatch(String first, String second) {
            channelOne.emit(MessageBatch.create(List.of(Message.builder(first)
                                                                .header("key", "batch-first")
                                                                .build(),
                                                        Message.builder(second)
                                                                .header("key", "batch-second")
                                                                .build())));
        }

        void emitChannelTwo(String entity) {
            channelTwo.emit(entity);
        }

        void emitFailingChannel(String entity) {
            failingChannel.emit(entity);
        }

        void emitCheckedFailingChannel(String entity) {
            checkedFailingChannel.emit(entity);
        }

        boolean emittersInjected() {
            return channelOne != null && channelTwo != null && failingChannel != null && checkedFailingChannel != null;
        }
    }

    @Service.Singleton
    static class FirstChannelOneConsumer extends MessageConsumer {
        private final List<String> keys = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(CHANNEL_ONE)
        void consume(@Messaging.HeaderParam("key") String key,
                     Message<String> message) {
            keys.add(key);
            messages().add(message);
        }

        List<String> keys() {
            return keys;
        }
    }

    @Service.Singleton
    static class SecondChannelOneConsumer extends MessageConsumer {
        @Messaging.ReceiveFrom(CHANNEL_ONE)
        void consume(Message<String> message) {
            messages().add(message);
        }
    }

    @Service.Singleton
    static class BatchChannelOneConsumer {
        private final List<MessageBatch<String>> batches = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(CHANNEL_ONE)
        void consume(MessageBatch<String> batch) {
            batches.add(batch);
        }

        List<MessageBatch<String>> batches() {
            return batches;
        }
    }

    @Service.Singleton
    static class ChannelTwoConsumer extends MessageConsumer {
        @Messaging.ReceiveFrom(CHANNEL_TWO)
        void consume(String payload) {
            messages().add(Message.builder(payload).build());
        }
    }

    @Service.Singleton
    static class CustomMessageConsumer {
        private final List<CustomMessage<String, Integer>> messages = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(CUSTOM_MESSAGE_CHANNEL)
        void consume(CustomMessage<String, Integer> message) {
            messages.add(message);
        }

        List<CustomMessage<String, Integer>> messages() {
            return messages;
        }
    }

    @Service.Singleton
    static class BroadCustomMessageConsumer {
        private final List<Message<Integer>> messages = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(CUSTOM_MESSAGE_CHANNEL)
        void consume(Message<Integer> message) {
            messages.add(message);
        }

        List<Message<Integer>> messages() {
            return messages;
        }
    }

    @Service.Singleton
    static class MultiHopMessageConsumer {
        private final List<MultiHopMessage<String, List<Integer>>> messages = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(MULTI_HOP_MESSAGE_CHANNEL)
        void consume(MultiHopMessage<String, List<Integer>> message) {
            messages.add(message);
        }

        List<MultiHopMessage<String, List<Integer>>> messages() {
            return messages;
        }
    }

    @Service.Singleton
    static class ForwardingProcessor {
        private final MessagingException failure = new MessagingException("processor failed",
                                                                          new IOException("processor I/O failed"));

        @Messaging.ReceiveFrom(FORWARDING_INPUT_CHANNEL)
        @Messaging.SendTo(FORWARDING_OUTPUT_CHANNEL)
        Message<String> forward(String payload) {
            if ("processor-fail".equals(payload)) {
                throw failure;
            }
            return Message.builder("forwarded: " + payload)
                    .header("processor", "forwarding")
                    .build();
        }

        MessagingException failure() {
            return failure;
        }
    }

    @Service.Singleton
    static class PayloadProcessor {
        @Messaging.ReceiveFrom(PAYLOAD_PROCESSOR_INPUT_CHANNEL)
        @Messaging.SendTo(PAYLOAD_PROCESSOR_OUTPUT_CHANNEL)
        String process(String payload) {
            return "processed: " + payload;
        }
    }

    @Service.Singleton
    static class PayloadProcessorConsumer extends MessageConsumer {
        @Messaging.ReceiveFrom(PAYLOAD_PROCESSOR_OUTPUT_CHANNEL)
        void consume(Message<String> message) {
            messages().add(message);
        }
    }

    @Service.Singleton
    static class ArrayEnvelopeProcessor {
        @Messaging.ReceiveFrom(ARRAY_PROCESSOR_INPUT_CHANNEL)
        @Messaging.SendTo(ARRAY_PROCESSOR_OUTPUT_CHANNEL)
        ArrayMessage<String> process(String payload) {
            String processed = "processed: " + payload;
            return new ImmutableArrayMessage<>(new String[][] {{payload}, {processed}}, MessageHeaders.empty());
        }
    }

    @Service.Singleton
    static class ArrayPayloadConsumer {
        private final List<String[][]> payloads = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(ARRAY_PROCESSOR_OUTPUT_CHANNEL)
        void consume(String[][] payload) {
            payloads.add(payload);
        }

        List<String[][]> payloads() {
            return payloads;
        }
    }

    @Service.Singleton
    static class RequiredHeaderConsumer {
        private final List<HeaderDelivery> deliveries = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(REQUIRED_HEADER_CHANNEL)
        void consume(@Messaging.Entity String payload,
                     @Messaging.HeaderParam("required") String required) {
            deliveries.add(new HeaderDelivery(payload, required));
        }

        List<HeaderDelivery> deliveries() {
            return deliveries;
        }
    }

    @Service.Singleton
    static class OptionalHeaderConsumer {
        private final List<OptionalHeaderDelivery> deliveries = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(OPTIONAL_HEADER_CHANNEL)
        void consume(@Messaging.Entity String payload,
                     @Messaging.HeaderParam("trace-id") Optional<String> traceId) {
            deliveries.add(new OptionalHeaderDelivery(payload, traceId));
        }

        List<OptionalHeaderDelivery> deliveries() {
            return deliveries;
        }
    }

    @Service.Singleton
    static class TypedHeaderConsumer {
        private final List<TypedHeaderDelivery> deliveries = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(TYPED_HEADER_CHANNEL)
        void consume(@Messaging.Entity String payload,
                     @Messaging.HeaderParam("required") MessageHeaderValue required,
                     @Messaging.HeaderParam("optional") Optional<MessageHeaderValue> optional,
                     @Messaging.HeaderParam("repeated") List<MessageHeaderValue> repeated) {
            deliveries.add(new TypedHeaderDelivery(payload, required, optional, repeated));
        }

        List<TypedHeaderDelivery> deliveries() {
            return deliveries;
        }
    }

    @Service.Singleton
    static class ForwardedMessageConsumer extends MessageConsumer {
        private final MessagingException failure = new MessagingException("downstream handler failed",
                                                                          new IOException("downstream I/O failed"));

        @Messaging.ReceiveFrom(FORWARDING_OUTPUT_CHANNEL)
        void consume(Message<String> message) {
            if (message.entity().equals("forwarded: fail")) {
                throw failure;
            }
            messages().add(message);
        }

        MessagingException failure() {
            return failure;
        }
    }

    @Service.Singleton
    static class ForwardedBatchConsumer {
        private final List<MessageBatch<String>> batches = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(FORWARDING_OUTPUT_CHANNEL)
        void consume(MessageBatch<String> batch) {
            batches.add(batch);
        }

        List<MessageBatch<String>> batches() {
            return batches;
        }
    }

    @Service.Singleton
    static class FailingConsumer {
        private final MessagingException failure = new MessagingException("handler failed",
                                                                          new IOException("handler I/O failed"));

        @Messaging.ReceiveFrom(FAILING_CHANNEL)
        void consume(String payload) {
            throw failure;
        }

        MessagingException failure() {
            return failure;
        }
    }

    @Service.Singleton
    static class CheckedFailingConsumer {
        private final IOException failure = new IOException("checked handler failed");

        @Messaging.ReceiveFrom(CHECKED_FAILING_CHANNEL)
        void consume(String payload) throws IOException {
            throw failure;
        }

        IOException failure() {
            return failure;
        }
    }

    @Service.Singleton
    static class AnnotatedFailureConsumer {
        private final AtomicInteger attempts = new AtomicInteger();

        @Messaging.ReceiveFrom(ANNOTATED_FAILURE_CHANNEL)
        @Messaging.OnFailure(retryDelay = "PT0.001S",
                             maxAttempts = 2,
                             onExhausted = FailureDisposition.DEAD_LETTER,
                             deadLetterChannel = ANNOTATED_FAILURE_DLQ_CHANNEL)
        void consume(String payload) {
            attempts.incrementAndGet();
            throw new MessagingException("annotated handler failed");
        }

        int attempts() {
            return attempts.get();
        }
    }

    @Service.Singleton
    static class AnnotatedFailureDeadLetterConsumer {
        private final List<DeadLetterMessage<String>> messages = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(ANNOTATED_FAILURE_DLQ_CHANNEL)
        void consume(DeadLetterMessage<String> message) {
            messages.add(message);
        }

        List<DeadLetterMessage<String>> messages() {
            return messages;
        }
    }

    @Service.PerLookup
    static class PerLookupInterceptedConsumer {
        private static final Queue<PerLookupInterceptedConsumer> INSTANCES = new ConcurrentLinkedQueue<>();

        @Messaging.ReceiveFrom(PER_LOOKUP_INTERCEPTED_CHANNEL)
        void consume(String payload) {
            INSTANCES.add(this);
        }

        static void reset() {
            INSTANCES.clear();
        }

        static List<PerLookupInterceptedConsumer> instances() {
            return List.copyOf(INSTANCES);
        }
    }

    @SuppressWarnings({"deprecation", "helidon:api:incubating"})
    @Service.Singleton
    static class TestEntryPointInterceptor implements Interception.EntryPointInterceptor {
        private static final Queue<String> EXECUTIONS = new ConcurrentLinkedQueue<>();
        private static final Queue<InterceptedInstance> INTERCEPTED_INSTANCES = new ConcurrentLinkedQueue<>();

        @Override
        public <T> T proceed(InterceptionContext invocationContext,
                             Interception.Interceptor.Chain<T> chain,
                             Object... args) throws Exception {
            String serviceType = invocationContext.serviceInfo().serviceType().fqName();
            EXECUTIONS.add(serviceType + "." + invocationContext.elementInfo().signature().text());
            INTERCEPTED_INSTANCES.add(new InterceptedInstance(serviceType,
                                                              invocationContext.serviceInstance().orElseThrow()));
            return chain.proceed(args);
        }

        static void reset() {
            EXECUTIONS.clear();
            INTERCEPTED_INSTANCES.clear();
        }

        static List<String> executions() {
            return List.copyOf(EXECUTIONS);
        }

        static List<Object> serviceInstances(Class<?> serviceType) {
            return INTERCEPTED_INSTANCES.stream()
                    .filter(instance -> instance.serviceType().equals(serviceType.getCanonicalName()))
                    .map(InterceptedInstance::serviceInstance)
                    .toList();
        }

        private record InterceptedInstance(String serviceType, Object serviceInstance) {
        }
    }

    @Service.Singleton
    static class TestConnectorObserver {
        private final CountDownLatch deliveryCompleted = new CountDownLatch(1);
        private final AtomicReference<RuntimeException> deliveryFailure = new AtomicReference<>();

        void deliveryCompleted(RuntimeException failure) {
            deliveryFailure.set(failure);
            deliveryCompleted.countDown();
        }

        boolean awaitDelivery() throws InterruptedException {
            return deliveryCompleted.await(10, TimeUnit.SECONDS);
        }

        Optional<RuntimeException> deliveryFailure() {
            return Optional.ofNullable(deliveryFailure.get());
        }
    }

    @Service.Singleton
    static class TestIncomingConnectorProvider implements IncomingConnectorProvider {
        private final TestConnectorObserver observer;

        @Service.Inject
        TestIncomingConnectorProvider(TestConnectorObserver observer) {
            this.observer = observer;
        }

        @Override
        public String connectorType() {
            return TEST_CONNECTOR;
        }

        @Override
        public IncomingConnector createIncomingConnector(Config config) {
            return new TestIncomingConnector(observer);
        }

        private static final class TestIncomingConnector implements IncomingConnector {
            private final TestConnectorObserver observer;
            private final CountDownLatch stop = new CountDownLatch(1);
            private final AtomicBoolean stopped = new AtomicBoolean();

            private TestIncomingConnector(TestConnectorObserver observer) {
                this.observer = observer;
            }

            @Override
            public void run(IncomingConnectorContext context) {
                if (!context.awaitRunning() || stopped.get()) {
                    return;
                }
                RuntimeException failure = null;
                MessageBatch<String> batch = MessageBatch.create(
                        Message.builder("connector message")
                                .header("key", "connector")
                                .build());
                try (var reservation = context.reserveDelivery();
                     var delivery = reservation.start(batch)) {
                    delivery.await();
                } catch (RuntimeException e) {
                    failure = e;
                } finally {
                    observer.deliveryCompleted(failure);
                }
                await(stop);
            }

            @Override
            public void drain() {
                stopped.set(true);
                stop.countDown();
            }

            @Override
            public void forceClose() {
                drain();
            }

            @Override
            public void close() {
                forceClose();
            }
        }
    }

    @Service.Singleton
    static class ShutdownConsumer {
        private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

        @Messaging.ReceiveFrom(SHUTDOWN_CHANNEL)
        void consume(String ignored) {
        }

        @Service.PreDestroy
        void close() {
            EVENTS.add("consumer-close");
        }

        static List<String> events() {
            return EVENTS;
        }
    }

    @Service.Singleton
    static class ShutdownSingletonConsumer {
        private static final List<String> EVENTS = new CopyOnWriteArrayList<>();
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();
        private static final AtomicReference<CountDownLatch> FIRST_ENTERED = new AtomicReference<>();
        private static final AtomicReference<CountDownLatch> RELEASE_FIRST = new AtomicReference<>();

        @Messaging.ReceiveFrom(SHUTDOWN_SINGLETON_CHANNEL)
        void consume(String ignored) {
            int invocation = INVOCATIONS.incrementAndGet();
            if (invocation == 1) {
                EVENTS.add("first-enter");
                FIRST_ENTERED.get().countDown();
                await(RELEASE_FIRST.get());
                EVENTS.add("first-exit");
            } else {
                EVENTS.add("second");
            }
        }

        @Service.PreDestroy
        void close() {
            EVENTS.add("consumer-close");
        }

        static void reset() {
            EVENTS.clear();
            INVOCATIONS.set(0);
            FIRST_ENTERED.set(new CountDownLatch(1));
            RELEASE_FIRST.set(new CountDownLatch(1));
        }

        static boolean awaitFirst() throws InterruptedException {
            return FIRST_ENTERED.get().await(5, TimeUnit.SECONDS);
        }

        static void releaseFirst() {
            RELEASE_FIRST.get().countDown();
        }

        static int invocations() {
            return INVOCATIONS.get();
        }

        static List<String> events() {
            return List.copyOf(EVENTS);
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 2)
    static class ShutdownSingletonProbe {
        private static final AtomicReference<CountDownLatch> SHUTDOWN_STARTED =
                new AtomicReference<>(new CountDownLatch(0));

        @Service.PreDestroy
        void close() {
            SHUTDOWN_STARTED.get().countDown();
        }

        static void reset() {
            SHUTDOWN_STARTED.set(new CountDownLatch(1));
        }

        static boolean awaitShutdown() throws InterruptedException {
            return SHUTDOWN_STARTED.get().await(5, TimeUnit.SECONDS);
        }
    }

    @Service.Singleton
    static class ShutdownIncomingConnectorProvider implements IncomingConnectorProvider {
        @Override
        public String connectorType() {
            return SHUTDOWN_CONNECTOR;
        }

        @Override
        public IncomingConnector createIncomingConnector(Config config) {
            return new ShutdownConnector();
        }

        private static final class ShutdownConnector implements IncomingConnector {
            private final CountDownLatch stop = new CountDownLatch(1);

            @Override
            public void run(IncomingConnectorContext context) {
                if (context.awaitRunning()) {
                    ShutdownConsumer.events().add("source-start");
                    await(stop);
                }
            }

            @Override
            public void drain() {
                ShutdownConsumer.events().add("source-stop");
                stop.countDown();
            }

            @Override
            public void forceClose() {
                stop.countDown();
            }

            @Override
            public void close() {
                forceClose();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    abstract static class MessageConsumer {
        private final List<Message<String>> messages = new CopyOnWriteArrayList<>();

        List<Message<String>> messages() {
            return messages;
        }
    }

    record HeaderDelivery(String payload, String header) {
    }

    record OptionalHeaderDelivery(String payload, Optional<String> header) {
    }

    record TypedHeaderDelivery(String payload,
                               MessageHeaderValue required,
                               Optional<MessageHeaderValue> optional,
                               List<MessageHeaderValue> repeated) {
    }

    interface CustomMessage<K, V> extends Message<V> {
        K key();
    }

    interface ArrayMessage<T> extends Message<T[][]> {
    }

    static final class ImmutableArrayMessage<T> implements ArrayMessage<T> {
        private final T[][] entity;
        private final MessageHeaders headers;

        ImmutableArrayMessage(T[][] entity, MessageHeaders headers) {
            this.entity = entity;
            this.headers = headers;
        }

        @Override
        public T[][] entity() {
            return entity;
        }

        @Override
        public MessageHeaders headers() {
            return headers;
        }
    }

    static final class ImmutableCustomMessage<K, V> implements CustomMessage<K, V> {
        private final K key;
        private final V entity;
        private final MessageHeaders headers;

        ImmutableCustomMessage(K key, V entity, MessageHeaders headers) {
            this.key = key;
            this.entity = entity;
            this.headers = headers;
        }

        @Override
        public K key() {
            return key;
        }

        @Override
        public V entity() {
            return entity;
        }

        @Override
        public MessageHeaders headers() {
            return headers;
        }
    }

    interface IntermediateMessage<P, I> extends Message<P> {
        I key();
    }

    interface MultiHopMessage<K, V> extends IntermediateMessage<V, K> {
    }

    static final class ImmutableMultiHopMessage<K, V> implements MultiHopMessage<K, V> {
        private final K key;
        private final V entity;
        private final MessageHeaders headers;

        ImmutableMultiHopMessage(K key, V entity, MessageHeaders headers) {
            this.key = key;
            this.entity = entity;
            this.headers = headers;
        }

        @Override
        public K key() {
            return key;
        }

        @Override
        public V entity() {
            return entity;
        }

        @Override
        public MessageHeaders headers() {
            return headers;
        }
    }
}
