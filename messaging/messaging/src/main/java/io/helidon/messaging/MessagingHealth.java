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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.service.registry.GlobalServiceRegistry;

/**
 * Graph-owned health membership whose checks retain only detached status.
 */
final class MessagingHealth {
    private static final System.Logger LOGGER = System.getLogger(MessagingHealth.class.getName());
    private static final AtomicLong GRAPH_SEQUENCE = new AtomicLong();
    private static final MessagingHealth DISABLED = new MessagingHealth();

    private final long id;
    private final MessagingHealthProvider provider;
    private final StateCell state;
    private final List<HealthCheck> checks;

    private MessagingHealth() {
        id = 0;
        provider = null;
        state = null;
        checks = List.of();
    }

    private MessagingHealth(MessagingHealthProvider provider) {
        this.id = GRAPH_SEQUENCE.incrementAndGet();
        this.provider = provider;
        this.state = new StateCell();
        this.checks = List.of(check("messaging-" + id + "-ready", HealthCheckType.READINESS, state::snapshot),
                              check("messaging-" + id + "-live", HealthCheckType.LIVENESS, state::snapshot));
        if (provider != null) {
            provider.register(id, state);
        }
    }

    static MessagingHealth create(MessagingConfig config) {
        if (!config.enableHealth()) {
            return DISABLED;
        }
        try {
            var registry = config.serviceRegistry().orElseGet(GlobalServiceRegistry::registry);
            return new MessagingHealth(registry.first(MessagingHealthProvider.class).orElse(null));
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot register messaging health; using explicit graph checks", e);
            return new MessagingHealth(null);
        }
    }

    static HealthCheck check(String name, HealthCheckType type, Supplier<Snapshot> snapshot) {
        return new Check(name, type, snapshot);
    }

    List<HealthCheck> checks() {
        return checks;
    }

    void state(DefaultMessagingGraph.State newState, Throwable failure) {
        if (state != null) {
            String failureType = failure == null ? "" : failure.getClass().getName();
            state.snapshot = new Snapshot(newState, failureType.substring(0, Math.min(256, failureType.length())));
        }
    }

    void close() {
        if (provider != null) {
            provider.remove(id);
        }
    }

    record Snapshot(DefaultMessagingGraph.State state, String failureType) {
        boolean ready() {
            return state == DefaultMessagingGraph.State.RUNNING;
        }

        boolean live() {
            return state != DefaultMessagingGraph.State.FAILED;
        }
    }

    static final class StateCell {
        private volatile Snapshot snapshot = new Snapshot(DefaultMessagingGraph.State.NEW, "");

        Snapshot snapshot() {
            return snapshot;
        }
    }

    private static final class Check implements HealthCheck {
        private final String name;
        private final HealthCheckType type;
        private final Supplier<Snapshot> snapshot;

        private Check(String name, HealthCheckType type, Supplier<Snapshot> snapshot) {
            this.name = name;
            this.type = type;
            this.snapshot = snapshot;
        }

        @Override
        public HealthCheckType type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public HealthCheckResponse call() {
            Snapshot current = snapshot.get();
            var response = HealthCheckResponse.builder()
                    .status(type == HealthCheckType.READINESS ? current.ready() : current.live())
                    .detail("state", current.state().name());
            if (!current.failureType().isEmpty()) {
                response.detail("failure-type", current.failureType());
            }
            return response.build();
        }
    }
}
