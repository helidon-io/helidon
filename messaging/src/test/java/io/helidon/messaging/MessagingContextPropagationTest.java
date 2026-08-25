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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@Timeout(10)
class MessagingContextPropagationTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String MARKER = MessagingContextPropagationTest.class.getName();

    @Test
    void localEmitterUsesCallerContextOnVirtualDeliveryThread() {
        Context callerContext = Context.create();
        Object marker = new Object();
        callerContext.register(MARKER, marker);
        Thread callerThread = Thread.currentThread();
        Optional<Context> previousContext = Contexts.context();
        AtomicReference<Context> handlerContext = new AtomicReference<>();
        AtomicReference<Thread> handlerThread = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("orders", String.class);
        builder.payloadSink(channel, ignored -> {
            handlerContext.set(currentContext());
            handlerThread.set(Thread.currentThread());
        });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            Contexts.runInContext(callerContext, () -> graph.emitter(channel).emit("order"));
        }

        assertThat(handlerContext.get(), sameInstance(callerContext));
        assertThat(handlerContext.get().get(MARKER, Object.class).orElseThrow(), sameInstance(marker));
        assertThat(handlerThread.get().isVirtual(), is(true));
        assertThat(handlerThread.get() == callerThread, is(false));
        assertThat(Contexts.context(), is(previousContext));
    }

    @Test
    void connectorDeliveriesUseFreshIsolatedContexts() throws InterruptedException {
        Context connectorContext = Context.create();
        Object connectorMarker = new Object();
        connectorContext.register(MARKER, connectorMarker);
        AtomicReference<Context> firstContext = new AtomicReference<>();
        AtomicReference<Context> secondContext = new AtomicReference<>();
        AtomicReference<Thread> firstThread = new AtomicReference<>();
        RuntimeException mappingFailure = new RuntimeException("mapping failed");
        AtomicReference<RuntimeException> observedMappingFailure = new AtomicReference<>();

        try (DeliveryEngine engine = engine("orders")) {
            startConnectorDelivery(engine, connectorContext, "first", firstContext, firstThread);
            startFailedConnectorDelivery(engine,
                                         connectorContext,
                                         "second",
                                         mappingFailure,
                                         secondContext,
                                         observedMappingFailure);
        }

        assertThat(firstContext.get() == connectorContext, is(false));
        assertThat(firstContext.get() == Contexts.globalContext(), is(false));
        assertThat(firstContext.get().get(MARKER, Object.class).isEmpty(), is(true));
        assertThat(secondContext.get() == connectorContext, is(false));
        assertThat(secondContext.get() == Contexts.globalContext(), is(false));
        assertThat(secondContext.get().get(MARKER, Object.class).isEmpty(), is(true));
        assertThat(firstContext.get() == secondContext.get(), is(false));
        assertThat(firstThread.get().isVirtual(), is(true));
        assertThat(observedMappingFailure.get(), sameInstance(mappingFailure));
    }

    @Test
    void contextlessLocalDeliveryCreatesFreshContext() throws Exception {
        AtomicReference<Optional<Context>> firstCallerContext = new AtomicReference<>();
        AtomicReference<Optional<Context>> secondCallerContext = new AtomicReference<>();
        AtomicReference<Context> firstHandlerContext = new AtomicReference<>();
        AtomicReference<Context> secondHandlerContext = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("orders", String.class);
        builder.payloadSink(channel, value -> {
            if (value.equals("first")) {
                firstHandlerContext.set(currentContext());
            } else {
                secondHandlerContext.set(currentContext());
            }
        });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            runWithoutContext(() -> graph.emitter(channel).emit("first"), firstCallerContext)
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            runWithoutContext(() -> graph.emitter(channel).emit("second"), secondCallerContext)
                    .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        assertThat(firstCallerContext.get(), is(Optional.empty()));
        assertThat(secondCallerContext.get(), is(Optional.empty()));
        assertThat(firstHandlerContext.get() == Contexts.globalContext(), is(false));
        assertThat(secondHandlerContext.get() == Contexts.globalContext(), is(false));
        assertThat(firstHandlerContext.get() == secondHandlerContext.get(), is(false));
    }

    @Test
    void processorsAndRoutesRetainOneCallerContext() {
        Context callerContext = Context.create();
        Object marker = new Object();
        callerContext.register(MARKER, marker);
        AtomicReference<Context> processorContext = new AtomicReference<>();
        AtomicReference<Context> intermediateContext = new AtomicReference<>();
        AtomicReference<Context> targetContext = new AtomicReference<>();
        AtomicReference<Thread> processorThread = new AtomicReference<>();
        AtomicReference<Thread> intermediateThread = new AtomicReference<>();
        AtomicReference<Thread> targetThread = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> input = builder.channel("input", String.class);
        MessagingChannel<String> intermediate = builder.channel("intermediate", String.class);
        MessagingChannel<String> target = builder.channel("target", String.class);
        builder.payloadProcessor(input, intermediate, value -> {
                    processorContext.set(currentContext());
                    processorThread.set(Thread.currentThread());
                    return value;
                })
                .payloadSink(intermediate, ignored -> {
                    intermediateContext.set(currentContext());
                    intermediateThread.set(Thread.currentThread());
                })
                .route(intermediate, target)
                .payloadSink(target, ignored -> {
                    targetContext.set(currentContext());
                    targetThread.set(Thread.currentThread());
                });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            Contexts.runInContext(callerContext, () -> graph.emitter(input).emit("order"));
        }

        for (Context context : List.of(processorContext.get(), intermediateContext.get(), targetContext.get())) {
            assertThat(context, sameInstance(callerContext));
            assertThat(context.get(MARKER, Object.class).orElseThrow(), sameInstance(marker));
        }
        List<Thread> threads = List.of(processorThread.get(), intermediateThread.get(), targetThread.get());
        assertThat(threads.stream().allMatch(Thread::isVirtual), is(true));
        assertThat(new HashSet<>(threads).size(), is(3));
    }

    @Test
    void directChildEmissionRecoversContextFromActiveAncestry() {
        Context callerContext = Context.create();
        Object marker = new Object();
        callerContext.register(MARKER, marker);
        AtomicReference<Emitter<String>> childEmitter = new AtomicReference<>();
        AtomicReference<Optional<Context>> childCallerContext = new AtomicReference<>();
        AtomicReference<Context> childHandlerContext = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> parent = builder.channel("parent", String.class);
        MessagingChannel<String> child = builder.channel("child", String.class);
        builder.payloadSink(parent, ignored -> await(
                        runWithoutContext(() -> childEmitter.get().emit("child"), childCallerContext)))
                .payloadSink(child, ignored -> childHandlerContext.set(currentContext()));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            childEmitter.set(graph.emitter(child));

            Contexts.runInContext(callerContext, () -> graph.emitter(parent).emit("parent"));
        }

        assertThat(childCallerContext.get(), is(Optional.empty()));
        assertThat(childHandlerContext.get(), sameInstance(callerContext));
        assertThat(childHandlerContext.get().get(MARKER, Object.class).orElseThrow(), sameInstance(marker));
    }

    @Test
    void concurrentDeliveriesDoNotLeakContexts() throws Exception {
        Context firstCaller = Context.create();
        Context secondCaller = Context.create();
        firstCaller.register(MARKER, "first");
        secondCaller.register(MARKER, "second");
        CountDownLatch bothEntered = new CountDownLatch(2);
        AtomicReference<Context> firstHandlerContext = new AtomicReference<>();
        AtomicReference<Context> secondHandlerContext = new AtomicReference<>();
        AtomicReference<Thread> firstHandlerThread = new AtomicReference<>();
        AtomicReference<Thread> secondHandlerThread = new AtomicReference<>();
        AtomicReference<Optional<Context>> firstRestoredContext = new AtomicReference<>();
        AtomicReference<Optional<Context>> secondRestoredContext = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> first = builder.channel("first", String.class);
        MessagingChannel<String> second = builder.channel("second", String.class);
        builder.payloadSink(first, ignored -> {
                    firstHandlerContext.set(currentContext());
                    firstHandlerThread.set(Thread.currentThread());
                    awaitTogether(bothEntered);
                })
                .payloadSink(second, ignored -> {
                    secondHandlerContext.set(currentContext());
                    secondHandlerThread.set(Thread.currentThread());
                    awaitTogether(bothEntered);
                });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            CompletableFuture<Void> firstDelivery = runInContext(
                    firstCaller,
                    () -> graph.emitter(first).emit("first"),
                    firstRestoredContext);
            CompletableFuture<Void> secondDelivery = runInContext(
                    secondCaller,
                    () -> graph.emitter(second).emit("second"),
                    secondRestoredContext);

            firstDelivery.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            secondDelivery.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        assertThat(firstHandlerContext.get(), sameInstance(firstCaller));
        assertThat(secondHandlerContext.get(), sameInstance(secondCaller));
        assertThat(firstHandlerContext.get().get(MARKER, String.class).orElseThrow(), is("first"));
        assertThat(secondHandlerContext.get().get(MARKER, String.class).orElseThrow(), is("second"));
        assertThat(firstHandlerContext.get() == secondHandlerContext.get(), is(false));
        assertThat(firstHandlerThread.get().isVirtual(), is(true));
        assertThat(secondHandlerThread.get().isVirtual(), is(true));
        assertThat(firstHandlerThread.get() == secondHandlerThread.get(), is(false));
        assertThat(firstRestoredContext.get(), is(Optional.empty()));
        assertThat(secondRestoredContext.get(), is(Optional.empty()));
    }

    private static void startConnectorDelivery(DeliveryEngine engine,
                                               Context connectorContext,
                                               String value,
                                               AtomicReference<Context> handlerContext,
                                               AtomicReference<Thread> handlerThread) throws InterruptedException {
        try (ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery(
                "orders",
                1,
                ignored -> {
                    handlerContext.set(currentContext());
                    handlerThread.set(Thread.currentThread());
                });
             ConnectorDelivery delivery = Contexts.runInContext(connectorContext,
                                                                () -> reservation.start(batch(value)))) {
            assertThat(delivery.await(WAIT), is(true));
        }
    }

    private static void startFailedConnectorDelivery(DeliveryEngine engine,
                                                     Context connectorContext,
                                                     String value,
                                                     RuntimeException mappingFailure,
                                                     AtomicReference<Context> handlerContext,
                                                     AtomicReference<RuntimeException> observedFailure)
            throws InterruptedException {
        try (ConnectorDeliveryReservation reservation = engine.reserveConnectorDelivery(
                "orders",
                1,
                ignored -> { },
                (ignored, failure) -> {
                    handlerContext.set(currentContext());
                    observedFailure.set(failure);
                });
             ConnectorDelivery delivery = Contexts.runInContext(
                     connectorContext,
                     () -> reservation.startFailed(batch(value), mappingFailure))) {
            assertThat(delivery.await(WAIT), is(true));
        }
    }

    private static DeliveryEngine engine(String... channels) {
        MessagingExecutionConfig config = MessagingExecutionConfig.builder().build();
        DeliveryEngine engine = new DeliveryEngine(config);
        for (String channel : channels) {
            engine.registerChannel(channel, config);
        }
        return engine;
    }

    private static MessageBatch<String> batch(String value) {
        return MessageBatch.create(Message.create(value));
    }

    private static Context currentContext() {
        return Contexts.context().orElseThrow();
    }

    private static void awaitTogether(CountDownLatch latch) {
        latch.countDown();
        try {
            assertThat(latch.await(WAIT.toMillis(), TimeUnit.MILLISECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent deliveries", e);
        }
    }

    private static CompletableFuture<Void> runInContext(Context context,
                                                        Runnable action,
                                                        AtomicReference<Optional<Context>> restoredContext) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                Contexts.runInContext(context, action);
                restoredContext.set(Contexts.context());
                result.complete(null);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    private static CompletableFuture<Void> runWithoutContext(
            Runnable action,
            AtomicReference<Optional<Context>> callerContext) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            callerContext.set(Contexts.context());
            try {
                action.run();
                result.complete(null);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    private static void await(CompletableFuture<Void> future) {
        try {
            future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new AssertionError("Child emission did not complete", e);
        }
    }
}
