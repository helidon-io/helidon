/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Support for all observe providers that are available (or configured).
 */
@RuntimeType.PrototypedBy(ObserveFeatureConfig.class)
public class ObserveFeature implements ServerFeature, Weighted, RuntimeType.Api<ObserveFeatureConfig> {
    static final String OBSERVE_ID = "observe";
    static final double WEIGHT = 80;

    private final List<Observer> observers;
    private final boolean enabled;
    private final String endpoint;
    private final double weight;
    private final ObserveFeatureConfig config;

    private ObserveFeature(ObserveFeatureConfig config, List<Observer> observers) {
        this.config = config;
        this.enabled = config.enabled();
        this.endpoint = config.endpoint();
        this.weight = config.weight();
        this.observers = observers;
    }

    /**
     * A new builder to customize observe support.
     *
     * @return a new builder
     */
    public static ObserveFeatureConfig.Builder builder() {
        return ObserveFeatureConfig.builder();
    }

    /**
     * Create a new observe feature customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observe feature
     */
    public static ObserveFeature create(Consumer<ObserveFeatureConfig.Builder> consumer) {
        return ObserveFeatureConfig.builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new observe feature based on its configuration.
     *
     * @param config configuration
     * @return a new observe feature
     */
    public static ObserveFeature create(ObserveFeatureConfig config) {
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

        return new ObserveFeature(config, uniqueObservers);
    }

    /**
     * Create a new support with default configuration and an explicit list of observers.
     * This will NOT use providers discovered by {@link java.util.ServiceLoader}.
     *
     * @param observers observer to use
     * @return a new observe support
     */
    public static ObserveFeature just(Observer... observers) {
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
     * Create a new support with default configuration and an explicit list of observers.
     * This will use providers discovered by {@link java.util.ServiceLoader}.
     *
     * @param observers observer to use
     * @return a new observe support
     */
    public static ObserveFeature create(Observer... observers) {
        return builder()
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
    public ObserveFeatureConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return OBSERVE_ID;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        List<String> sockets = config.sockets();

        List<HttpRouting.Builder> observeEndpointRouting;
        if (sockets.isEmpty()) {
            observeEndpointRouting = List.of(featureContext.socket(WebServer.DEFAULT_SOCKET_NAME).httpRouting());
        } else {
            observeEndpointRouting = sockets.stream()
                    .map(it -> featureContext.socket(it).httpRouting())
                    .toList();
        }
        // we must guarantee that each observer adding its own HttpFeature adds it with correct weight
        if (enabled) {
            for (Observer observer : observers) {
                observer.register(featureContext, observeEndpointRouting, endpoint(config.endpoint()));
            }
        } else {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                builder.any(endpoint + "/*", (req, res) -> {
                    throw new HttpException("Observe endpoint is disabled", Status.SERVICE_UNAVAILABLE_503, true);
                });
            }
        }
    }

    @Override
    public double weight() {
        return weight;
    }

    private static UnaryOperator<String> endpoint(String observeEndpoint) {
        return observerEndpoint -> {
            if (observerEndpoint.startsWith("/")) {
                return observerEndpoint;
            }
            if (observeEndpoint.endsWith("/")) {
                return observeEndpoint + observerEndpoint;
            }
            return observeEndpoint + "/" + observerEndpoint;
        };
    }
}
