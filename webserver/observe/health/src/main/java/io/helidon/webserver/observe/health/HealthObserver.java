/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.DisabledObserverFeature;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Observer that registers health endpoint, and collects all health checks.
 */
@RuntimeType.PrototypedBy(HealthObserverConfig.class)
public class HealthObserver implements Observer, RuntimeType.Api<HealthObserverConfig> {
    private final HealthObserverConfig config;
    private final List<HealthCheck> all;

    private HealthObserver(HealthObserverConfig config) {
        this.config = config;

        List<HealthCheck> checks = new ArrayList<>(config.healthChecks());
        if (config.useSystemServices()) {
            Config cfg = config.config().orElseGet(Config::empty);
            HelidonServiceLoader.create(ServiceLoader.load(HealthCheckProvider.class))
                    .asList()
                    .stream()
                    .map(provider -> provider.healthChecks(cfg))
                    .flatMap(Collection::stream)
                    .forEach(checks::add);
        }
        // checks now contain all health checks we want to use in this instance
        this.all = List.copyOf(checks);
    }

    /**
     * Create a health observer, adding the provided checks to the checks discovered via {@link java.util.ServiceLoader}
     * and {@link io.helidon.health.spi.HealthCheckProvider}.
     *
     * @param healthChecks health checks to add
     * @return a new observer to register with {@link io.helidon.webserver.observe.ObserveFeature}
     */
    public static HealthObserver create(HealthCheck... healthChecks) {
        return builder()
                .useSystemServices(false)
                .addHealthChecks(Arrays.asList(healthChecks))
                .build();
    }

    /**
     * Create a new builder to configure Health observer.
     *
     * @return a new builder
     */
    public static HealthObserverConfig.Builder builder() {
        return HealthObserverConfig.builder();
    }

    /**
     * Create a new Health observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static HealthObserver create(HealthObserverConfig config) {
        return new HealthObserver(config);
    }

    /**
     * Create a new Health observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static HealthObserver create(Consumer<HealthObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public void register(ServerFeature.ServerFeatureContext featureContext,
                         List<HttpRouting.Builder> observeEndpointRouting,
                         UnaryOperator<String> endpointFunction) {

        String endpoint = endpointFunction.apply(config.endpoint());
        if (config.enabled()) {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                // we must use a feature to honor weight of the observer feature itself
                builder.addFeature(new HealthHttpFeature(endpoint, config, all));
            }
        } else {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                builder.addFeature(DisabledObserverFeature.create("Health", endpoint + "/*"));
            }
        }
    }

    @Override
    public String type() {
        return "health";
    }

    @Override
    public HealthObserverConfig prototype() {
        return config;
    }

    private static class HealthHttpFeature implements HttpFeature {
        private final String endpoint;
        private final HealthObserverConfig config;
        private final List<HealthCheck> all;

        private HealthHttpFeature(String endpoint, HealthObserverConfig config, List<HealthCheck> all) {
            this.endpoint = endpoint;
            this.config = config;
            this.all = all;
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.register(endpoint, new HealthService(config, all));
        }
    }
}
