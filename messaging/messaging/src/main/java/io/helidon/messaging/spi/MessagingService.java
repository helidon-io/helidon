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

package io.helidon.messaging.spi;

import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.config.NamedService;
import io.helidon.messaging.MessageBatch;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Configured service for observing batch dispatch in a messaging graph.
 */
@Api.Preview
public interface MessagingService extends NamedService {
    /**
     * Open a service instance for one graph.
     *
     * @param registry owning service registry
     * @return graph-owned service instance
     */
    Instance create(ServiceRegistry registry);

    /**
     * Service resources and channel observations for one graph.
     */
    interface Instance extends AutoCloseable {
        /**
         * Decorate dispatch during channel assembly. The returned consumer can be called concurrently.
         * It must invoke dispatch exactly once and preserve its outcome, including any failure.
         * Return dispatch itself when observation is disabled.
         *
         * @param channel channel name
         * @param dispatch synchronous processing of one batch attempt
         * @return dispatch operation for this channel
         */
        Consumer<MessageBatch<?>> observe(String channel, Consumer<MessageBatch<?>> dispatch);

        /**
         * Release this graph's observations. Completion of forced shutdown may race in-flight dispatch.
         * This callback runs synchronously during shutdown or rollback and must return promptly.
         * It runs outside the graph's shutdown timeout. Do not wait for in-flight dispatch, perform blocking I/O,
         * or flush an exporter here. A stalled callback delays completion for all callers waiting for graph shutdown.
         */
        @Override
        void close();
    }
}
