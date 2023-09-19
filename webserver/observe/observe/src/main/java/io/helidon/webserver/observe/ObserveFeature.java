/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;

/**
 * Support for all observe providers that are available (or configured).
 */
@RuntimeType.PrototypedBy(ObserveConfig.class)
public class ObserveFeature implements HttpFeature, Weighted, RuntimeType.Api<ObserveConfig> {
    private final List<ObserverSetup> providers;
    private final boolean enabled;
    private final String endpoint;
    private final double weight;
    private final ObserveConfig config;

    private ObserveFeature(ObserveConfig config, List<ObserverSetup> providerSetups) {
        this.config = config;
        this.enabled = config.enabled();
        this.endpoint = config.endpoint();
        this.weight = config.weight();
        this.providers = providerSetups;
    }

    /**
     * A new builder to customize observe support.
     *
     * @return a new builder
     */
    public static ObserveConfig.Builder builder() {
        return ObserveConfig.builder();
    }

    /**
     * Create a new observe feature customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observe feature
     */
    public static ObserveFeature create(Consumer<ObserveConfig.Builder> consumer) {
        return ObserveConfig.builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new observe feature based on its configuration.
     *
     * @param config configuration
     * @return a new observe feature
     */
    public static ObserveFeature create(ObserveConfig config) {
        List<Observer> observers = config.observers();

        // by type first, by name second
        Map<String, Map<String, Observer>> uniqueObserversMap = new HashMap<>();
        List<Observer> uniqueObservers = new ArrayList<>();

        for (Observer observer : observers) {
            // if the same name exists, ignore it (service loader is loaded after custom observers)
            if (uniqueObserversMap.computeIfAbsent(observer.type(), it -> new HashMap<>())
                    .putIfAbsent(observer.name(), observer) == null) {
                // a unique mapping
                uniqueObservers.add(observer);
            }
        }

        List<ObserverSetup> observerSetups = new ArrayList<>();

        String observeEndpoint = config.endpoint();
        for (Observer observer : uniqueObservers) {
            CrossOriginConfig cors = observer.prototype().cors().orElse(config.cors());
            boolean enabled = observer.prototype().enabled();
            observerSetups.add(new ObserverSetup(it -> endpoint(observeEndpoint, it),
                                                 enabled,
                                                 cors,
                                                 observer));
        }

        return new ObserveFeature(config, observerSetups);
    }

    /**
     * Create a new support with default configuration and an explicit list of observers.
     * This will NOT use providers discovered by {@link java.util.ServiceLoader}.
     *
     * @param observers observer to use
     * @return a new observe support
     */
    public static ObserveFeature create(Observer... observers) {
        return builder()
                .observersDiscoverServices(false)
                .update(it -> {
                    for (Observer observer : observers) {
                        it.addObserver(observer);
                    }
                })
                .build();
    }

    /**
     * Create a new support with default configuration and a list of providers
     * discovered by {@link java.util.ServiceLoader}.
     *
     * @return a new observe support
     */
    public static ObserveFeature create() {
        return builder().build();
    }

    /**
     * Create a new support with custom configuration.
     *
     * @param config configuration to read observe config from
     * @return a new observe support
     */
    public static ObserveFeature create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public ObserveConfig prototype() {
        return config;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        if (enabled) {
            for (ObserverSetup provider : providers) {
                provider.observer().register(routing, provider.endpointFn(), provider.cors);
            }
        } else {
            routing.get(endpoint, (req, res) -> {
                throw new HttpException("Observe endpoint is disabled", Status.SERVICE_UNAVAILABLE_503, true);
            });
        }
    }

    @Override
    public double weight() {
        return weight;
    }

    private static String endpoint(String observeEndpoint, String observerEndpoint) {
        if (observerEndpoint.startsWith("/")) {
            return observerEndpoint;
        }
        if (observeEndpoint.endsWith("/")) {
            return observeEndpoint + observerEndpoint;
        }
        return observeEndpoint + "/" + observerEndpoint;
    }

    private record ObserverSetup(UnaryOperator<String> endpointFn,
                                 boolean enabled,
                                 CrossOriginConfig cors,
                                 Observer observer) {
    }
}
