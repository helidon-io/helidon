/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.function.Function;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.json.JsonSupport;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * Support for metrics for Helidon Web Server.
 *
 * <p>
 * By defaults cretes the /metrics endpoint with three
 * sub-paths: application, vendor and base.
 * <p>
 * To register with web server:
 * <pre>{@code
 * Routing.builder()
 *        .register(MetricsSupport.create())
 * }</pre>
 * <p>
 * This class supports finer grained configuration using Helidon Config: {@link #create(Config)}.
 * The following configuration parameters can be used:
 * <table border="1">
 * <caption>Configuration parameters</caption>
 * <tr><th>key</th><th>default value</th><th>description</th></tr>
 * <tr><td>helidon.metrics.context</td><td>/metrics</td><td>Context root under which the rest endpoints are available</td></tr>
 * <tr><td>helidon.metrics.base.${metricName}.enabled</td><td>true</td><td>Can control which base metrics are exposed, set to
 * false
 * to disable a base metric</td></tr>
 * </table>
 * <p>
 * The application metrics registry is then available as follows:
 * <pre>{@code
 *  req.context().get(MetricRegistry.class).ifPresent(reg -> reg.counter("myCounter").inc());
 * }</pre>
 */
public final class MetricsSupport implements Service {
    private static final String DEFAULT_CONTEXT = "/metrics";
    private final Registry base;
    private final Registry app;
    private final Registry vendor;
    private final String context;
    private final RegistryFactory rf;
    private final Counter totalCount;
    private final Meter totalMeter;

    private static MetricsSupport metricsSupport;

    private MetricsSupport(Builder builder) {
        this.rf = RegistryFactory.create(builder.config);
        this.base = rf.getARegistry(MetricRegistry.Type.BASE);
        this.app = rf.getARegistry(MetricRegistry.Type.APPLICATION);
        this.vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        this.context = builder.context;

        this.totalCount = vendor.counter(new Metadata("requests.count",
                                                      "Total number of requests",
                                                      "Each request (regardless of HTTP method) will increase this counter",
                                                      MetricType.COUNTER,
                                                      MetricUnits.NONE));
        this.totalMeter = vendor.meter(new Metadata("requests.meter",
                                                    "Meter for overall requests",
                                                    "Each request will mark the meter to see overall throughput",
                                                    MetricType.METERED,
                                                    MetricUnits.NONE));
    }

    /**
     * Create an instance to be registered with Web Server with all defaults.
     *
     * @return a new instance built with default values (for context, base metrics enabled)
     */
    public static synchronized MetricsSupport create() {
        if (metricsSupport == null) {
            metricsSupport = builder().build();
        }
        return metricsSupport;
    }

    /**
     * Create an instance to be registered with Web Server maybe overriding default values with
     * configured values.
     *
     * @param config Config instance to use to (maybe) override configuration of this component. See class javadoc for supported
     *               configuration keys.
     * @return a new instance configured withe config provided
     */
    public static synchronized MetricsSupport create(Config config) {
        if (metricsSupport == null) {
            metricsSupport = builder().config(config).build();
        }
        return metricsSupport;
    }

    /**
     * Create a new builder to construct an instance.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private static void getAll(ServerRequest req, ServerResponse res, Registry registry) {
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            res.send(toJsonData(registry));
        } else {
            res.send(toPrometheusData(registry));
        }
    }

    private static void optionsAll(ServerRequest req, ServerResponse res, Registry registry) {
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            res.send(toJsonMeta(registry));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }

    }

    static String toPrometheusData(Registry... registries) {
        StringBuilder result = new StringBuilder();

        for (Registry registry : registries) {
            result.append(toPrometheusData(registry));
        }

        return result.toString();
    }

    static String toPrometheusData(Registry registry) {
        StringBuilder result = new StringBuilder();

        registry.stream()
                .forEach(mpMetric -> result.append(mpMetric.prometheusData()));

        return result.toString();
    }

    /**
     * Returns the Prometheus data for the specified {@link Metric}.
     * <p>
     * Not every {@code Metric} supports conversion to Prometheus data. This
     * method checks the metric first before performing the conversion,
     * throwing an {@code IllegalArgumentException} if the metric cannot be
     * converted.
     *
     * @param metric the {@code Metric} to convert to Prometheus format
     * @return {@code String} containing the Prometheus data
     */
    public static String toPrometheusData(Metric metric) {
        return checkMetricTypeThenRun(metric, HelidonMetric::prometheusData);
    }

    private static String checkMetricTypeThenRun(Metric metric, Function<HelidonMetric, String> fn) {
        if (metric == null) {
            throw new NullPointerException();
        }
        if (!(metric instanceof HelidonMetric)) {
            throw new IllegalArgumentException(String.format(
                "Metric of type %s is expected to implement %s but does not",
                    metric.getClass().getName(),
                    HelidonMetric.class.getName()));
        }
        return fn.apply((HelidonMetric) metric);
    }

    // unit testable
    static JsonObject toJsonData(Registry... registries) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Registry registry : registries) {
            if (!registry.empty()) {
                builder.add(registry.type(), toJsonData(registry));
            }
        }
        return builder.build();
    }

    static JsonObject toJsonData(Registry registry) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        registry.stream()
                .forEach(mpMetric -> mpMetric.jsonData(builder));
        return builder.build();
    }

    static JsonObject toJsonMeta(Registry... registries) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Registry registry : registries) {
            if (!registry.empty()) {
                builder.add(registry.type(), toJsonMeta(registry));
            }
        }
        return builder.build();
    }

    static JsonObject toJsonMeta(Registry registry) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        registry.stream()
                .forEach(mpMetric -> mpMetric.jsonMeta(builder));
        return builder.build();
    }

    @Override
    public void update(Routing.Rules rules) {
        // register the metric registry and factory to be available to all
        rules.any((req, res) -> {
            req.context().register(app);
            req.context().register(rf);
            totalCount.inc();
            totalMeter.mark();
            req.next();
        });

        rules.anyOf(CollectionsHelper.listOf(Http.Method.GET, Http.Method.OPTIONS),
                    JsonSupport.get());

        // routing to root of metrics
        rules.get(context, (req, res) -> getMultiple(req, res, base, app, vendor))
                .options(context, (req, res) -> optionsMultiple(req, res, base, app, vendor));

        // routing to each scope
        Stream.of(app, base, vendor)
                .forEach(registry -> {
                    String type = registry.type();

                    rules.get(context + "/" + type, (req, res) -> getAll(req, res, registry))
                            .get(context + "/" + type + "/{metric}", (req, res) -> getOne(req, res, registry))
                            .options(context + "/" + type, (req, res) -> optionsAll(req, res, registry))
                            .options(context + "/" + type + "/{metric}", (req, res) -> optionsOne(req, res, registry));
                });
    }

    private void getOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        OptionalHelper.from(registry.getMetric(metricName))
                .ifPresentOrElse(metric -> {
                    if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        metric.jsonData(builder);
                        res.send(builder.build());
                    } else {
                        res.send(metric.prometheusData());
                    }
                }, () -> {
                    res.status(Http.Status.NOT_FOUND_404);
                    res.send();
                });
    }

    private void getMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            res.send(toJsonData(registries));
        } else {
            res.send(toPrometheusData(registries));
        }
    }

    private void optionsMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            res.send(toJsonMeta(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        OptionalHelper.from(registry.getMetric(metricName))
                .ifPresentOrElse(metric -> {
                    if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        metric.jsonMeta(builder);
                        res.send(builder.build());
                    } else {
                        res.status(Http.Status.NOT_ACCEPTABLE_406);
                        res.send();
                    }
                }, () -> {
                    res.status(Http.Status.NO_CONTENT_204);
                    res.send();
                });
    }

    /**
     * A fluent API builder to build instances of {@link MetricsSupport}.
     */
    public static final class Builder implements io.helidon.common.Builder<MetricsSupport> {
        private String context = DEFAULT_CONTEXT;
        private Config config = Config.empty();

        private Builder() {

        }

        @Override
        public MetricsSupport build() {
            return new MetricsSupport(this);
        }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see MetricsSupport for details about configuration keys
         */
        public Builder config(Config config) {
            this.config = config;
            config.get("helidon.metrics.context").value().ifPresent(this::context);

            return this;
        }

        /**
         * Set a new root context for REST API of metrics.
         *
         * @param newContext context to use
         * @return updated builder instance
         */
        public Builder context(String newContext) {
            this.context = newContext;
            return this;
        }
    }
}
