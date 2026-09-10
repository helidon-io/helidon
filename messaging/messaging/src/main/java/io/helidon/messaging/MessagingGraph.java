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

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;

/**
 * One messaging topology and lifecycle.
 * <p>
 * A graph owns all channels, sources, routes, connectors, and their lifecycle. The topology is mutable only
 * through its builder and is frozen by {@link MessagingConfig.Builder#build()}.
 * Use each builder and its configuration for one graph only; do not reuse them after a build attempt, including
 * a failed build. This ownership requirement is not enforced by the builder.
 */
@Api.Preview
public interface MessagingGraph extends RuntimeType.Api<MessagingConfig>, AutoCloseable {
    /**
     * Create a builder for one graph. The builder must not be reused after a build attempt.
     *
     * @return graph builder
     */
    static MessagingConfig.Builder builder() {
        return MessagingConfig.builder();
    }

    /**
     * Create a graph with customized configuration.
     *
     * @param consumer messaging configuration builder consumer
     * @return messaging graph
     */
    static MessagingGraph create(Consumer<MessagingConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Create a graph from its immutable configuration.
     *
     * @param config messaging configuration
     * @return messaging graph
     */
    static MessagingGraph create(MessagingConfig config) {
        return new MessagingGraphAssembler(config).graph();
    }

    /**
     * Create a graph from the messaging configuration node.
     *
     * @param config messaging configuration node
     * @return messaging graph
     */
    static MessagingGraph create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Validate and start the complete graph, waiting for outgoing connector startup and incoming connector readiness.
     * <p>
     * The core runtime does not impose a startup deadline. ChannelConnection transport configuration may define its own
     * connection or readiness limits. Waiting in this method is interruptible; concurrent {@link #close()} cancels
     * startup.
     *
     * @return this graph
     */
    MessagingGraph start();

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
     * <p>
     * When called from a delivery, source task, or connector lifecycle callback owned by this graph, shutdown is
     * handed off so the current task can complete. Such a call returns after initiating shutdown; any eventual failure
     * is recorded and reported to a later waiting caller. A call from any other thread waits for shutdown to complete.
     *
     * @throws MessagingException if shutdown cannot be initiated, or if a waiting caller observes a managed source
     *                            failure or shutdown that cannot complete cleanly
     */
    @Override
    void close();

}
