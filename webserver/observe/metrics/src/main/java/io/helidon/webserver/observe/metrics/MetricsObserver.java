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

package io.helidon.webserver.observe.metrics;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Support for metrics for Helidon WebServer.
 *
 * <p>
 * By default, creates the {@code /metrics} endpoint with three sub-paths: application,
 * vendor and base.
 * <p>
 * To register with web server, discovered from classpath:
 * <pre>{@code
 * Routing.builder()
 *        .register(ObserveFeature.create())
 * }</pre>
 *
 * See {@link io.helidon.webserver.observe.ObserveFeature#just(io.helidon.webserver.observe.spi.Observer...)}
 * to customize observer setup.
 * <p>
 * This class supports finer grained configuration using Helidon Config:
 * {@link #create(io.helidon.common.config.Config)}.
 * <p>
 * The application metrics registry is then available as follows:
 * <pre>{@code
 *  req.context().get(MetricRegistry.class).ifPresent(reg -> reg.counter("myCounter").inc());
 * }</pre>
 */
@RuntimeType.PrototypedBy(MetricsObserverConfig.class)
public class MetricsObserver implements Observer, RuntimeType.Api<MetricsObserverConfig> {
    private final MetricsObserverConfig config;
    private MetricsFeature metricsFeature;

    private MetricsObserver(MetricsObserverConfig config) {
        this.config = config;
        this.metricsFeature = new MetricsFeature(config);
    }

    /**
     * Create a new builder to configure Metrics observer.
     *
     * @return a new builder
     */
    public static MetricsObserverConfig.Builder builder() {
        return MetricsObserverConfig.builder();
    }

    /**
     * Create a new Metrics observer using the provided configuration.
     *
     * @param config configuration
     * @return a new observer
     */
    public static MetricsObserver create(MetricsObserverConfig config) {
        return new MetricsObserver(config);
    }

    /**
     * Create a new Metrics observer customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new observer
     */
    public static MetricsObserver create(Consumer<MetricsObserverConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a new Metrics observer with default configuration.
     *
     * @return a new observer
     */
    public static MetricsObserver create() {
        return builder()
                .build();
    }

    /**
     * Create a new Metrics observer from configuration.
     *
     * @param config configuration of this observer
     * @return a new observer
     */
    public static MetricsObserver create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    @Override
    public MetricsObserverConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return "metrics";
    }

    @Override
    public void register(ServerFeature.ServerFeatureContext featureContext,
                         List<HttpRouting.Builder> observeEndpointRouting,
                         UnaryOperator<String> endpointFunction) {
        String endpoint = endpointFunction.apply(config.endpoint());

        if (config.enabled()) {
            for (HttpRouting.Builder routing : observeEndpointRouting) {
                // register the service itself
                metricsFeature.register(routing, endpoint);
            }
        } else {
            for (HttpRouting.Builder builder : observeEndpointRouting) {
                builder.any(endpoint + "/*", (req, res) -> {
                    throw new HttpException("Metrics endpoint is disabled", Status.SERVICE_UNAVAILABLE_503, true);
                });
            }
        }
    }

    /**
     * Configure Helidon specific metrics.
     *
     * @param rules rules to use
     */
    public void configureVendorMetrics(HttpRouting.Builder rules) {
        metricsFeature.configureVendorMetrics(rules);
    }
}
