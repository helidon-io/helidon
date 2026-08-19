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

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.registry.Service;

/**
 * Ensures messaging admission stops before application consumer services are destroyed.
 * <p>
 * The graph itself starts at the messaging run level. This guard is activated as its dependency and owns only the
 * earlier shutdown signal; the graph's own pre-destroy hook remains as an idempotent fallback.
 */
@Service.Singleton
@Service.RunLevel(Double.MAX_VALUE)
final class MessagingLifecycleGuard {
    private final AtomicReference<ChannelRegistry> registry = new AtomicReference<>();

    @Service.Inject
    MessagingLifecycleGuard() {
    }

    void register(ChannelRegistry channelRegistry) {
        if (!registry.compareAndSet(null, channelRegistry)) {
            throw new IllegalStateException("A messaging graph is already registered");
        }
    }

    @Service.PreDestroy
    void close() {
        ChannelRegistry channelRegistry = registry.getAndSet(null);
        if (channelRegistry != null) {
            channelRegistry.close();
        }
    }
}
