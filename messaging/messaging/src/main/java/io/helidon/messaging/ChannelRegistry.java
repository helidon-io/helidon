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

import java.util.Optional;

import io.helidon.service.registry.Service;

/**
 * Service-registry bootstrap for the shared messaging graph.
 */
@Service.Singleton
@Service.RunLevel(MessagingRuntime.RUN_LEVEL)
class ChannelRegistry implements MessagingRuntime {
    private final DefaultMessagingGraph graph;

    @Service.Inject
    ChannelRegistry(MessagingConfig config, MessagingLifecycleGuard lifecycleGuard) {
        this.graph = (DefaultMessagingGraph) config.build();
        try {
            graph.prepare();
        } catch (RuntimeException | Error e) {
            graph.abortPreparation(e);
            throw e;
        }
        lifecycleGuard.register(this);
    }

    @Override
    public <T> void emitBatch(String channel, MessageBatch<? extends T> messages) {
        graph.emitBatch(channel, messages);
    }

    @Service.PreDestroy
    public void close() {
        graph.close();
    }

    Optional<MessagingChannel<?>> channel(String channel) {
        return graph.channel(channel);
    }

    @Service.PostConstruct
    void start() {
        graph.start();
    }

    IncomingConnectorContext incomingContext(String channel) {
        return new DefaultIncomingConnectorContext(graph, channel, FailurePolicy.create());
    }
}
