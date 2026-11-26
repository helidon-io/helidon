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

package io.helidon.messaging.metrics;

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.messaging.spi.MessagingService;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Records completed batch processing attempts for each channel of a messaging graph.
 * Select this service using {@code messaging.services.metrics} or add it to the graph builder.
 * Meters are shared by graphs using the same meter registry and channel tags.
 */
@Api.Preview
public final class MessagingMetrics implements MessagingService, RuntimeType.Api<MessagingMetricsConfig> {
    private final MessagingMetricsConfig config;

    private MessagingMetrics(MessagingMetricsConfig config) {
        this.config = config;
    }

    /**
     * Creates a builder for the service.
     *
     * @return a new builder
     */
    public static MessagingMetricsConfig.Builder builder() {
        return MessagingMetricsConfig.builder();
    }

    /**
     * Creates an enabled service.
     *
     * @return a new service
     */
    public static MessagingMetrics create() {
        return builder().build();
    }

    /**
     * Creates a service from its configuration node.
     *
     * @param config service configuration
     * @return a new service
     */
    public static MessagingMetrics create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Creates a service from a configuration prototype.
     *
     * @param config service configuration
     * @return a new service
     */
    public static MessagingMetrics create(MessagingMetricsConfig config) {
        return new MessagingMetrics(Objects.requireNonNull(config, "config"));
    }

    /**
     * Creates a service with a customized configuration.
     *
     * @param consumer builder customizer
     * @return a new service
     */
    public static MessagingMetrics create(Consumer<MessagingMetricsConfig.Builder> consumer) {
        return builder().update(Objects.requireNonNull(consumer, "consumer")).build();
    }

    @Override
    public MessagingMetricsConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "metrics";
    }

    @Override
    public String name() {
        return type();
    }

    @Override
    public Instance create(ServiceRegistry registry) {
        return ChannelMetrics.create(config, Objects.requireNonNull(registry, "registry"));
    }
}
