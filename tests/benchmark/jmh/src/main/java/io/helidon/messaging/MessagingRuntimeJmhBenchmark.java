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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.config.Config;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * End-to-end messaging runtime benchmarks.
 * <p>
 * Run with the JMH GC profiler to collect allocation rates. Batch-size results are operations per second; multiply by
 * the {@code batchSize} parameter when comparing delivered messages per second.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
public class MessagingRuntimeJmhBenchmark {
    private static final String CHANNEL = "benchmark";
    private static final String PAYLOAD = "payload";

    /**
     * Invoke the same sink used by the runtime without messaging dispatch.
     *
     * @param state benchmark state
     * @return consumed value
     */
    @Benchmark
    public String directSink(RuntimeState state) {
        state.sink.accept(PAYLOAD);
        return state.consumed;
    }

    /**
     * Deliver one payload through an imperative messaging graph.
     *
     * @param state benchmark state
     * @return consumed value
     */
    @Benchmark
    public String emitPayload(RuntimeState state) {
        state.emitter.emit(PAYLOAD);
        return state.consumed;
    }

    /**
     * Construct a batch from pre-existing immutable message envelopes.
     *
     * @param state benchmark state
     * @param blackhole result consumer
     */
    @Benchmark
    public void constructBatch(BatchState state, Blackhole blackhole) {
        blackhole.consume(MessageBatch.create(state.messages));
    }

    /**
     * Deliver {@code batchSize} payloads as separate singleton emissions.
     *
     * @param state benchmark state
     * @return last consumed value
     */
    @Benchmark
    public String emitSingletons(BatchState state) {
        for (String payload : state.payloads) {
            state.emitter.emit(payload);
        }
        return state.consumed;
    }

    /**
     * Deliver {@code batchSize} prebuilt messages as separate singleton emissions. Compare this method with
     * {@link #emitPrebuiltBatch(BatchState)} to measure batching amortization from equal message-envelope inputs.
     *
     * @param state benchmark state
     * @return last consumed value
     */
    @Benchmark
    public String emitPrebuiltMessagesIndividually(BatchState state) {
        for (Message<String> message : state.messages) {
            state.emitter.emit(message);
        }
        return state.consumed;
    }

    /**
     * Construct and deliver {@code batchSize} messages as one batch.
     *
     * @param state benchmark state
     * @return last consumed value
     */
    @Benchmark
    public String emitBatch(BatchState state) {
        state.emitter.emit(MessageBatch.create(state.messages));
        return state.consumed;
    }

    /**
     * Deliver a prebuilt immutable batch to isolate runtime dispatch from batch construction.
     *
     * @param state benchmark state
     * @return last consumed value
     */
    @Benchmark
    public String emitPrebuiltBatch(BatchState state) {
        state.emitter.emit(state.batch);
        return state.consumed;
    }

    /**
     * Exercise the complete connector settlement path: reserve, start, await, and close.
     *
     * @param state benchmark state
     * @return last consumed value
     */
    @Benchmark
    public String settleIncomingConnectorDelivery(IncomingState state) {
        try (ConnectorDeliveryReservation reservation = state.context.reserveDelivery();
             ConnectorDelivery delivery = reservation.start(state.batch)) {
            delivery.await();
        }
        return state.consumed;
    }

    /**
     * One-message graph state shared by the direct and runtime paths.
     */
    @State(Scope.Thread)
    public static class RuntimeState {
        private final Consumer<String> sink = this::consume;
        private MessagingGraph graph;
        private Emitter<String> emitter;
        private volatile String consumed;

        /**
         * Create and start the graph once per trial.
         */
        @Setup(Level.Trial)
        public void setUp() {
            MessagingGraph.Builder builder = MessagingGraph.builder();
            MessagingChannel<String> channel = builder.channel(CHANNEL, String.class);
            builder.payloadSink(channel, sink);
            graph = builder.build();
            try {
                graph.start();
                emitter = graph.emitter(channel);
            } catch (RuntimeException | Error e) {
                closeAfterSetupFailure(graph::close, e);
                throw e;
            }
        }

        /**
         * Close the graph after a trial.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (graph != null) {
                graph.close();
            }
        }

        private void consume(String value) {
            consumed = value;
        }
    }

    /**
     * Parameterized state for singleton-versus-batch comparisons.
     */
    @State(Scope.Thread)
    public static class BatchState {
        /**
         * Messages delivered by one benchmark invocation.
         */
        @Param({"1", "32", "1024"})
        public int batchSize;

        private final Consumer<String> sink = this::consume;
        private List<String> payloads;
        private List<Message<String>> messages;
        private MessageBatch<String> batch;
        private MessagingGraph graph;
        private Emitter<String> emitter;
        private volatile String consumed;

        /**
         * Create the immutable input snapshots and start the graph once per trial.
         */
        @Setup(Level.Trial)
        public void setUp() {
            ArrayList<String> payloads = new ArrayList<>(batchSize);
            ArrayList<Message<String>> messages = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                String payload = PAYLOAD + '-' + i;
                payloads.add(payload);
                messages.add(Message.create(payload));
            }
            this.payloads = List.copyOf(payloads);
            this.messages = List.copyOf(messages);
            this.batch = MessageBatch.create(this.messages);

            MessagingGraph.Builder builder = MessagingGraph.builder();
            MessagingChannel<String> channel = builder.channel(CHANNEL, String.class);
            builder.payloadSink(channel, sink);
            graph = builder.build();
            try {
                graph.start();
                emitter = graph.emitter(channel);
            } catch (RuntimeException | Error e) {
                closeAfterSetupFailure(graph::close, e);
                throw e;
            }
        }

        /**
         * Close the graph after a trial.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (graph != null) {
                graph.close();
            }
        }

        private void consume(String value) {
            consumed = value;
        }
    }

    /**
     * Parameterized state for retained incoming-connector deliveries.
     */
    @State(Scope.Thread)
    public static class IncomingState {
        /**
         * Messages settled by one benchmark invocation.
         */
        @Param({"1", "32", "1024"})
        public int batchSize;

        private ChannelRegistry registry;
        private IncomingConnectorContext context;
        private MessageBatch<String> batch;
        private volatile String consumed;

        /**
         * Create the declarative runtime and retained delivery once per trial.
         */
        @Setup(Level.Trial)
        public void setUp() {
            List<Message<String>> messages = messages(batchSize);
            batch = MessageBatch.create(messages);
            ConsumerRegistration registration = new ConsumerRegistration() {
                @Override
                public String handlerId() {
                    return "benchmark-handler";
                }

                @Override
                public String channel() {
                    return CHANNEL;
                }

                @Override
                public Class<?> payloadType() {
                    return String.class;
                }

                @Override
                public void dispatch(MessageBatch<?> delivery) {
                    for (Message<?> message : delivery) {
                        consumed = (String) message.entity();
                    }
                }
            };

            registry = new ChannelRegistry(List.of(registration), Config.empty(), List.of());
            try {
                registry.start();
                context = registry.incomingContext(CHANNEL);
            } catch (RuntimeException | Error e) {
                closeAfterSetupFailure(registry::close, e);
                throw e;
            }
        }

        /**
         * Close the registry after a trial.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (registry != null) {
                registry.close();
            }
        }
    }

    private static List<Message<String>> messages(int size) {
        ArrayList<Message<String>> messages = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            messages.add(Message.create(PAYLOAD + '-' + i));
        }
        return List.copyOf(messages);
    }

    private static void closeAfterSetupFailure(Runnable closeable, Throwable failure) {
        try {
            closeable.run();
        } catch (RuntimeException | Error closeFailure) {
            if (failure != closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }
}
