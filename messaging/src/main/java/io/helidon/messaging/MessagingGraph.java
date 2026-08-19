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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.Api;
import io.helidon.common.GenericType;

/**
 * One imperative messaging topology and lifecycle.
 * <p>
 * A graph owns all channels, sources, routes, connectors, and their lifecycle. The topology is mutable only
 * through its builder and is frozen by {@link Builder#build()}.
 */
@Api.Preview
public interface MessagingGraph extends AutoCloseable {
    /**
     * Create a graph builder.
     *
     * @return graph builder
     */
    static Builder builder() {
        return new DefaultMessagingGraphBuilder();
    }

    /**
     * Validate and start the complete graph.
     */
    void start();

    /**
     * Obtain an imperative emitter for a channel owned by this graph.
     *
     * @param channel channel handle
     * @param <T> payload type
     * @return channel emitter
     * @throws IllegalArgumentException if the channel belongs to another graph
     * @throws IllegalStateException when an emission is attempted while the graph is not running
     */
    <T> Emitter<T> emitter(MessagingChannel<T> channel);

    /**
     * Stop admission, drain admitted work, and close all graph-owned resources.
     *
     * @throws MessagingException if a managed source failed asynchronously or shutdown cannot complete cleanly
     */
    @Override
    void close();

    /**
     * Builder of an imperative messaging graph.
     */
    interface Builder extends io.helidon.common.Builder<Builder, MessagingGraph>, AutoCloseable {
        /**
         * Configure default channel execution and graph shutdown behavior.
         * <p>
         * This must be configured before the first channel is declared.
         *
         * @param config execution configuration
         * @return updated builder
         */
        Builder executionConfig(MessagingExecutionConfig config);

        /**
         * Declare a channel.
         *
         * @param name channel name
         * @param payloadType payload type
         * @param <T> payload type
         * @return typed channel handle
         */
        <T> MessagingChannel<T> channel(String name, Class<T> payloadType);

        /**
         * Declare a channel while preserving parameterized payload type information.
         *
         * @param name channel name
         * @param payloadType payload type
         * @param <T> payload type
         * @return typed channel handle
         */
        <T> MessagingChannel<T> channel(String name, GenericType<T> payloadType);

        /**
         * Declare a channel with channel-specific execution limits.
         *
         * @param name channel name
         * @param payloadType payload type
         * @param executionConfig channel execution configuration
         * @param <T> payload type
         * @return typed channel handle
         */
        <T> MessagingChannel<T> channel(String name,
                                        GenericType<T> payloadType,
                                        MessagingExecutionConfig executionConfig);

        /**
         * Add a payload stream source.
         * <p>
         * The builder owns the stream after this method returns. Closing the builder or the built graph closes it.
         * A channel can have at most one stream source; explicit multi-source fan-in is not part of this API version.
         * Downstream paths of distinct stream sources must not converge on the same channel.
         *
         * @param channel target channel
         * @param source source stream
         * @param <T> payload type
         * @return updated builder
         * @throws IllegalArgumentException if the channel already has a stream source
         */
        <T> Builder payloadSource(MessagingChannel<T> channel, Stream<? extends T> source);

        /**
         * Add a message stream source.
         * <p>
         * The builder owns the stream after this method returns. Closing the builder or the built graph closes it.
         * A channel can have at most one stream source; explicit multi-source fan-in is not part of this API version.
         * Downstream paths of distinct stream sources must not converge on the same channel.
         *
         * @param channel target channel
         * @param source source stream
         * @param <T> payload type
         * @return updated builder
         * @throws IllegalArgumentException if the channel already has a stream source
         */
        <T> Builder messageSource(MessagingChannel<T> channel,
                                  Stream<? extends Message<? extends T>> source);

        /**
         * Route each delivery batch unchanged from one channel to another channel of the same type.
         *
         * @param source source channel
         * @param target target channel
         * @param <T> payload type
         * @return updated builder
         */
        <T> Builder route(MessagingChannel<T> source, MessagingChannel<T> target);

        /**
         * Add a payload processor. The processor is invoked once per batch item in order and its results form one
         * lineage-preserving derived batch. Message metadata is not propagated by a payload processor.
         *
         * @param source source channel
         * @param target target channel
         * @param processor payload processor
         * @param <I> input payload type
         * @param <O> output payload type
         * @return updated builder
         */
        <I, O> Builder payloadProcessor(MessagingChannel<I> source,
                                        MessagingChannel<O> target,
                                        Function<? super I, ? extends O> processor);

        /**
         * Add a message processor. The processor is invoked once per batch item in order and its results form one
         * lineage-preserving derived batch.
         *
         * @param source source channel
         * @param target target channel
         * @param processor message processor
         * @param <I> input payload type
         * @param <O> output payload type
         * @return updated builder
         */
        <I, O> Builder messageProcessor(MessagingChannel<I> source,
                                        MessagingChannel<O> target,
                                        Function<? super Message<I>, ? extends Message<? extends O>> processor);

        /**
         * Add a payload sink.
         *
         * @param source source channel
         * @param sink payload sink
         * @param <T> payload type
         * @return updated builder
         */
        <T> Builder payloadSink(MessagingChannel<T> source, Consumer<? super T> sink);

        /**
         * Add a message sink.
         *
         * @param source source channel
         * @param sink message sink
         * @param <T> payload type
         * @return updated builder
         */
        <T> Builder messageSink(MessagingChannel<T> source, Consumer<? super Message<T>> sink);

        /**
         * Add a message batch sink.
         *
         * @param source source channel
         * @param sink message batch sink
         * @param <T> payload type
         * @return updated builder
         */
        <T> Builder batchSink(MessagingChannel<T> source, Consumer<MessageBatch<T>> sink);

        /**
         * Add an outgoing connector as a required channel output.
         * <p>
         * The builder owns the connector after this method returns. Closing the builder or the built graph closes the
         * connector.
         *
         * @param source source channel
         * @param connector outgoing connector
         * @param <T> payload type
         * @return updated builder
         */
        <T> Builder outgoingConnector(MessagingChannel<T> source, OutgoingConnector connector);

        /**
         * Freeze and build the graph.
         *
         * @return immutable graph topology
         * @throws IllegalArgumentException if any channel has no required output or the topology is invalid
         */
        @Override
        MessagingGraph build();

        /**
         * Abandon this builder and close every stream and connector already registered with it.
         * <p>
         * After a successful {@link #build()}, resource ownership belongs to the returned graph and this method does
         * nothing.
         */
        @Override
        void close();
    }
}
