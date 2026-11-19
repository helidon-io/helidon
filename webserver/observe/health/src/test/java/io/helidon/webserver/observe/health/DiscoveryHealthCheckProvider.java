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

package io.helidon.webserver.observe.health;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.spi.HealthCheckProvider;

public final class DiscoveryHealthCheckProvider implements HealthCheckProvider {
    private final String name;
    private final AtomicInteger calls = new AtomicInteger();

    public DiscoveryHealthCheckProvider() {
        this("java-provider-check");
    }

    DiscoveryHealthCheckProvider(String name) {
        this.name = name;
    }

    @Override
    public List<HealthCheck> healthChecks(Config config) {
        calls.incrementAndGet();
        return List.of(HealthObserverDiscoveryTest.healthCheck(name));
    }

    int calls() {
        return calls.get();
    }
}
