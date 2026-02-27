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

package io.helidon.metrics.providers.micrometer;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.DefaultExemplarSampler;

/**
 * Metrics publisher for Prometheus output.
 */
public class PrometheusPublisher implements MicrometerMetricsPublisher,
                                            RuntimeType.Api<PrometheusPublisherConfig> {

    private final PrometheusPublisherConfig config;

    private PrometheusPublisher(PrometheusPublisherConfig config) {
        this.config = config;
    }

    /**
     * Returns a builder for constructing a Prometheus publisher.
     *
     * @return new builder
     */
    public static PrometheusPublisherConfig.Builder builder() {
        return PrometheusPublisherConfig.builder();
    }

    /**
     * Returns a default Prometheus publisher.
     *
     * @return default Prometheus publisher
     */
    public static PrometheusPublisher create() {
        return builder().build();
    }

    /**
     * Creates a new Prometheus published using the provided configuration.
     *
     * @param config Prometheus publisher config
     *
     * @return new Prometheus publisher
     */
    public static PrometheusPublisher create(PrometheusPublisherConfig config) {
        return new PrometheusPublisher(config);
    }

    /**
     * Creates a Prometheus publisher using a new builder and applying a consumer of the builder.
     *
     * @param consumer code to process a builder
     *
     * @return new Prometheus publisher
     */
    public static PrometheusPublisher create(Consumer<PrometheusPublisherConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    @Override
    public PrometheusPublisherConfig prototype() {
        return config;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public String name() {
        return PrometheusPublisherProvider.TYPE;
    }

    @Override
    public String type() {
        return PrometheusPublisherProvider.TYPE;
    }

    /**
     * Returns a factory function accepting a property look-up function and the Micrometer span context supplier provider
     * and producing a {@link io.micrometer.prometheus.PrometheusMeterRegistry}.
     *
     * @return factory function
     */
    public BiFunction<Function<String, String>, SpanContextSupplierProvider, PrometheusMeterRegistry> prometheusRegistry() {

        return (lookupFunction, spanContextSupplierProvider) ->
            spanContextSupplierProvider instanceof NoOpSpanContextSupplierProvider
                ? new PrometheusMeterRegistry(prometheusConfig(lookupFunction))
                : new PrometheusMeterRegistry(prometheusConfig(lookupFunction),
                                         new CollectorRegistry(),
                                         io.micrometer.core.instrument.Clock.SYSTEM,
                                         new DefaultExemplarSampler(spanContextSupplierProvider.get()));
    }

    @Override
    public Supplier<MeterRegistry> registry() {

        throw new UnsupportedOperationException("Prometheus publisher does not support registry(); use prometheusRegistry()");

    }

    private PrometheusConfig prometheusConfig(Function<String, String> propertyLookup) {

        return new PrometheusConfig() {

            @Override
            public String prefix() {
                return config.prefix().orElse(PrometheusConfig.super.prefix());
            }

            @Override
            public String get(String key) {
                return propertyLookup.apply(key);
            }

            @Override
            public boolean descriptions() {
                return config.descriptions().orElse(PrometheusConfig.super.descriptions());
            }

            @Override
            public Duration step() {
                return config.step().orElse(PrometheusConfig.super.step());
            }
        };
    }
}
