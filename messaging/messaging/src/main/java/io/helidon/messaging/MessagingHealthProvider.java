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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckType;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.service.registry.Service;

/**
 * Stable health checks backed by the graphs registered with one service registry.
 */
@Service.Singleton
@Service.ExternalContracts(HealthCheckProvider.class)
final class MessagingHealthProvider implements HealthCheckProvider {
    private final Map<Long, MessagingHealth.StateCell> graphs = new ConcurrentHashMap<>();
    private final List<HealthCheck> checks = List.of(
            MessagingHealth.check("messaging-ready", HealthCheckType.READINESS, this::snapshot),
            MessagingHealth.check("messaging-live", HealthCheckType.LIVENESS, this::snapshot));

    @Override
    public List<HealthCheck> healthChecks(Config config) {
        Objects.requireNonNull(config);
        return checks;
    }

    void register(long id, MessagingHealth.StateCell state) {
        graphs.put(id, state);
    }

    void remove(long id) {
        graphs.remove(id);
    }

    @Service.PreDestroy
    void close() {
        graphs.clear();
    }

    private MessagingHealth.Snapshot snapshot() {
        MessagingHealth.Snapshot result = new MessagingHealth.Snapshot(DefaultMessagingGraph.State.RUNNING, "");
        for (MessagingHealth.StateCell graph : graphs.values()) {
            MessagingHealth.Snapshot current = graph.snapshot();
            if (!current.live()) {
                return current;
            }
            if (!current.ready()) {
                result = current;
            }
        }
        return result;
    }
}
