/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Singleton;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

@Singleton
@SuppressWarnings("unused")
public class TestingCdiExtension implements Extension, LifecycleHook {
    static volatile boolean shutdownCalled;
    static final AtomicInteger running = new AtomicInteger();
    static final CopyOnWriteArrayList<Consumer<Object>> startupConsumers = new CopyOnWriteArrayList<>();
    static final CopyOnWriteArrayList<Consumer<Object>> shutdownConsumers = new CopyOnWriteArrayList<>();

    public TestingCdiExtension() {
    }

    void starting(@Observes @RuntimeStart Config config) {
        running.incrementAndGet();
        startupConsumers.forEach(c -> c.accept(config));
        startupConsumers.clear();
        shutdownCalled = false;
    }

    void stopping(@Observes @Priority(PLATFORM_AFTER) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        running.decrementAndGet();
        shutdownConsumers.forEach(c -> c.accept(event));
        shutdownCalled = !shutdownConsumers.isEmpty();
        shutdownConsumers.clear();
    }

    @Override
    public void registerStartupConsumer(Consumer<Object> consumer) {
        startupConsumers.add(consumer);
    }

    @Override
    public void registerShutdownConsumer(Consumer<Object> consumer) {
        shutdownConsumers.add(consumer);
    }

}
