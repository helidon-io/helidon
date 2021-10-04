/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Handler;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.LONG_RUNNING_REQUESTS_CONFIG_KEY;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsSettings.Builder.LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY;

/**
 * Support for metrics for Helidon Web Server.
 *
 * <p>
 * By defaults creates the /metrics endpoint with three sub-paths: application,
 * vendor and base.
 * <p>
 * To register with web server:
 * <pre>{@code
 * Routing.builder()
 *        .register(MetricsSupport.create())
 * }</pre>
 * <p>
 * This class supports finer grained configuration using Helidon Config:
 * {@link #create(Config)}. The following configuration parameters can be used:
 * <table border="1">
 * <caption>Configuration parameters</caption>
 * <tr><th>key</th><th>default value</th><th>description</th></tr>
 * <tr><td>helidon.metrics.context</td><td>/metrics</td><td>Context root under
 * which the rest endpoints are available</td></tr>
 * <tr><td>helidon.metrics.base.${metricName}.enabled</td><td>true</td><td>Can
 * control which base metrics are exposed, set to false to disable a base
 * metric</td></tr>
 * </table>
 * <p>
 * The application metrics registry is then available as follows:
 * <pre>{@code
 *  req.context().get(MetricRegistry.class).ifPresent(reg -> reg.counter("myCounter").inc());
 * }</pre>
 */
public final class MetricsSupport extends HelidonRestServiceSupport {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final String DEFAULT_CONTEXT = "/metrics";
    private static final String SERVICE_NAME = "Metrics";

    private static final MessageBodyWriter<JsonStructure> JSONP_WRITER = JsonpSupport.writer();

    private final RegistryFactory rf;

    private static KeyPerformanceIndicatorMetricsSettings kpiSettings;

    private static final Logger LOGGER = Logger.getLogger(MetricsSupport.class.getName());

    protected MetricsSupport(Builder builder) {
        super(LOGGER, builder, SERVICE_NAME);
        this.rf = builder.registryFactory.get();
        kpiSettings = builder.kpiSettingsBuilder.build();
    }

    /**
     * Create an instance to be registered with Web Server with all defaults.
     *
     * @return a new instance built with default values (for context, base
     * metrics enabled)
     */
    public static MetricsSupport create() {
        return MetricsSupport.builder().build();
    }

    /**
     * Create an instance to be registered with Web Server maybe overriding
     * default values with configured values.
     *
     * @param config Config instance to use to (maybe) override configuration of
     * this component. See class javadoc for supported configuration keys.
     * @return a new instance configured withe config provided
     */
    public static MetricsSupport create(Config config) {
        return builder().config(config).build();
    }

    static JsonObjectBuilder createMergingJsonObjectBuilder(JsonObjectBuilder delegate) {
        return new MergingJsonObjectBuilder(delegate);
    }

    // For testing
    static KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorMetricsConfig() {
        return kpiSettings;
    }

    /**
     * Create a new builder to construct an instance.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private static MediaType findBestAccepted(RequestHeaders headers) {
        Optional<MediaType> mediaType = headers.bestAccepted(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
        return mediaType.orElse(null);
    }

    /**
     * Derives the name prefix for KPI metrics based on the routing name (if any).
     *
     * @param routingName the routing name (empty string if none)
     * @return prefix for KPI metrics names incorporating the routing name
     */
    private static String metricsNamePrefix(String routingName) {
        return (null == routingName ? "" : routingName + ".") + KeyPerformanceIndicatorMetricsImpls.METRICS_NAME_PREFIX + ".";
    }


    private static void getAll(ServerRequest req, ServerResponse res, Registry registry) {
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        MediaType mediaType = findBestAccepted(req.headers());
        if (mediaType == MediaType.APPLICATION_JSON) {
            sendJson(res, toJsonData(registry));
        } else if (mediaType == MediaType.TEXT_PLAIN) {
            res.send(toPrometheusData(registry));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsAll(ServerRequest req, ServerResponse res, Registry registry) {
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            sendJson(res, toJsonMeta(registry));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }

    }

    static String toPrometheusData(Registry... registries) {
        return Arrays.stream(registries)
                .filter(r -> !r.empty())
                .map(MetricsSupport::toPrometheusData)
                .collect(Collectors.joining());
    }

    static String toPrometheusData(Registry registry) {
        StringBuilder builder = new StringBuilder();
        Set<String> serialized = new HashSet<>();
        registry.stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String name = entry.getKey().getName();
                    if (!serialized.contains(name)) {
                        toPrometheusData(builder, entry.getKey(), entry.getValue(), true);
                        serialized.add(name);
                    } else {
                        toPrometheusData(builder, entry.getKey(), entry.getValue(), false);
                    }
                });
        return builder.toString();
    }

    /**
     * Formats a metric in Prometheus format.
     *
     * @param metricID the {@code MetricID} for the metric to be formatted
     * @param metric the {@code Metric} containing the data to be formatted
     * @param withHelpType flag controlling serialization of HELP and TYPE
     * @return metric info in Prometheus format
     */
    public static String toPrometheusData(MetricID metricID, Metric metric, boolean withHelpType) {
        final StringBuilder sb = new StringBuilder();
        checkMetricTypeThenRun(sb, metricID, metric, withHelpType);
        return sb.toString();
    }

    /**
     * Formats a metric in Prometheus format.
     *
     * @param name the name of the metric
     * @param metric the {@code Metric} containing the data to be formatted
     * @param withHelpType flag controlling serialization of HELP and TYPE
     * @return metric info in Prometheus format
     */
    public static String toPrometheusData(String name, Metric metric, boolean withHelpType) {
        return toPrometheusData(new MetricID(name), metric, withHelpType);
    }

    /**
     * Returns the Prometheus data for the specified {@link Metric}.
     * <p>
     * Not every {@code Metric} supports conversion to Prometheus data. This
     * method checks the metric first before performing the conversion, throwing
     * an {@code IllegalArgumentException} if the metric cannot be converted.
     *
     * @param metricID the {@code MetricID} for the metric to convert
     * @param metric the {@code Metric} to convert to Prometheus format
     * @param withHelpType flag controlling serialization of HELP and TYPE
     */
    static void toPrometheusData(StringBuilder sb, MetricID metricID, Metric metric, boolean withHelpType) {
        checkMetricTypeThenRun(sb, metricID, metric, withHelpType);
    }

    private static void checkMetricTypeThenRun(StringBuilder sb, MetricID metricID, Metric metric,
                                               boolean withHelpType) {
        Objects.requireNonNull(metric);

        if (!(metric instanceof HelidonMetric)) {
            throw new IllegalArgumentException(String.format(
                    "Metric of type %s is expected to implement %s but does not",
                    metric.getClass().getName(),
                    HelidonMetric.class.getName()));
        }

        ((HelidonMetric) metric).prometheusData(sb, metricID, withHelpType);
    }

    // unit testable
    static JsonObject toJsonData(Registry... registries) {
        return toJson(MetricsSupport::toJsonData, registries);
    }

    static JsonObject toJsonData(Registry registry) {
        return toJson(
                (builder, entry) -> entry.getValue().jsonData(builder, entry.getKey()),
                registry);
    }

    static JsonObject toJsonMeta(Registry... registries) {
        return toJson(MetricsSupport::toJsonMeta, registries);
    }

    static JsonObject toJsonMeta(Registry registry) {
        return toJson((builder, entry) -> {
            final MetricID metricID = entry.getKey();
            final HelidonMetric metric = entry.getValue();
            final List<MetricID> sameNamedIDs = registry.metricIDsForName(metricID.getName());
            metric.jsonMeta(builder, sameNamedIDs);
                }, registry);
    }

    private static JsonObject toJson(Function<Registry, JsonObject> fn, Registry... registries) {
        return Arrays.stream(registries)
                .filter(r -> !r.empty())
                .collect(JSON::createObjectBuilder,
                        (builder, registry) -> accumulateJson(builder, registry, fn),
                        JsonObjectBuilder::addAll)
                .build();
    }

    private static void accumulateJson(JsonObjectBuilder builder, Registry registry,
            Function<Registry, JsonObject> fn) {
        builder.add(registry.type(), fn.apply(registry));
    }

    private static JsonObject toJson(
            BiConsumer<JsonObjectBuilder, ? super Map.Entry<MetricID, HelidonMetric>> accumulator,
            Registry registry) {

        return registry.stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .collect(() -> new MergingJsonObjectBuilder(JSON.createObjectBuilder()),
                        accumulator,
                        JsonObjectBuilder::addAll
                )
                .build();
    }

    /**
     * Configure vendor metrics on the provided routing. This method is
     * exclusive to {@link #update(io.helidon.webserver.Routing.Rules)} (e.g.
     * you should not use both, as otherwise you would duplicate the metrics)
     *
     * @param routingName name of the routing (may be null)
     * @param rules routing builder or routing rules
     */
    public void configureVendorMetrics(String routingName,
            Routing.Rules rules) {
        String metricPrefix = metricsNamePrefix(routingName);

        KeyPerformanceIndicatorSupport.Metrics kpiMetrics = KeyPerformanceIndicatorMetricsImpls.get(metricPrefix, kpiSettings);

        rules.any((req, res) -> {
            KeyPerformanceIndicatorSupport.Context kpiContext = kpiContext(req);

            kpiContext.requestHandlingStarted(kpiMetrics);
            res.whenSent()
                    .thenAccept(r -> kpiContext.requestProcessingCompleted(
                            r.status().code() < 500))
                    .exceptionallyAccept(t -> kpiContext.requestProcessingCompleted(false));
            Exception exception = null;
            try {
                req.next();
            } catch (Exception e) {
                exception = e;
                throw e;
            } finally {
                kpiContext.requestHandlingCompleted(exception == null);
            }
        });
    }

    /**
     * Finish configuring metrics endpoint on the provided routing rules. This method
     * just adds the endpoint {@code /metrics} (or appropriate one as
     * configured). For simple routings, just register {@code MetricsSupport}
     * instance. This method is exclusive to
     * {@link #update(io.helidon.webserver.Routing.Rules)} (e.g. you should not
     * use both, as otherwise you would register the endpoint twice)
     *
     * @param defaultRules routing rules for default routing (also accepts {@link io.helidon.webserver.Routing.Builder})
     * @param serviceEndpointRoutingRules possibly different rules for the metrics endpoint routing
     */
    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        Registry base = rf.getARegistry(MetricRegistry.Type.BASE);
        Registry vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        Registry app = rf.getARegistry(MetricRegistry.Type.APPLICATION);

        // register the metric registry and factory to be available to all
        MetricsContextHandler metricsContextHandler = new MetricsContextHandler(app, rf);
        defaultRules.any(metricsContextHandler);
        if (defaultRules != serviceEndpointRoutingRules) {
            serviceEndpointRoutingRules.any(metricsContextHandler);
        }

        configureVendorMetrics(null, defaultRules);

        // routing to root of metrics
        serviceEndpointRoutingRules.get(context(), (req, res) -> getMultiple(req, res, base, app, vendor))
                .options(context(), (req, res) -> optionsMultiple(req, res, base, app, vendor));

        // routing to each scope
        Stream.of(app, base, vendor)
                .forEach(registry -> {
                    String type = registry.type();

                    serviceEndpointRoutingRules.get(context() + "/" + type, (req, res) -> getAll(req, res, registry))
                            .get(context() + "/" + type + "/{metric}", (req, res) -> getByName(req, res, registry))
                            .options(context() + "/" + type, (req, res) -> optionsAll(req, res, registry))
                            .options(context() + "/" + type + "/{metric}", (req, res) -> optionsOne(req, res, registry));
                });
    }

    /**
     * Method invoked by the web server to update routing rules. Register this
     * instance with webserver through
     * {@link io.helidon.webserver.Routing.Builder#register(io.helidon.webserver.Service...)}
     * rather than calling this method directly. If multiple sockets (and
     * routings) should be supported, you can use the
     * {@link #configureEndpoint(io.helidon.webserver.Routing.Rules, io.helidon.webserver.Routing.Rules)}, and
     * {@link #configureVendorMetrics(String, io.helidon.webserver.Routing.Rules)}
     * methods.
     *
     * @param rules a routing rules to update
     */
    @Override
    public void update(Routing.Rules rules) {
        PeriodicExecutor.start();

        configureEndpoint(rules, rules);
    }

    @Override
    protected void onShutdown() {
        PeriodicExecutor.stop();
    }

    private static KeyPerformanceIndicatorSupport.Context kpiContext(ServerRequest request) {
        return request.context()
                .get(KeyPerformanceIndicatorSupport.Context.class)
                .orElseGet(KeyPerformanceIndicatorSupport.Context::create);
    }

    private void getByName(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        registry.getOptionalMetricEntry(metricName)
                .ifPresentOrElse(entry -> {
                    MediaType mediaType = findBestAccepted(req.headers());
                    if (mediaType == MediaType.APPLICATION_JSON) {
                        sendJson(res, jsonDataByName(registry, metricName));
                    } else if (mediaType == MediaType.TEXT_PLAIN) {
                        res.send(prometheusDataByName(registry, metricName));
                    } else {
                        res.status(Http.Status.NOT_ACCEPTABLE_406);
                        res.send();
                    }
                }, () -> {
                    res.status(Http.Status.NOT_FOUND_404);
                    res.send();
                });
    }

    static JsonObject jsonDataByName(Registry registry, String metricName) {
        JsonObjectBuilder builder = new MetricsSupport.MergingJsonObjectBuilder(JSON.createObjectBuilder());
        for (Map.Entry<MetricID, HelidonMetric> metricEntry : registry.getMetricsByName(metricName)) {
            metricEntry.getValue()
                    .jsonData(builder, metricEntry.getKey());
        }
        return builder.build();
    }

    static String prometheusDataByName(Registry registry, String metricName) {
        final StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Map.Entry<MetricID, HelidonMetric> metricEntry : registry.getMetricsByName(metricName)) {
            metricEntry.getValue()
                    .prometheusData(sb, metricEntry.getKey(), isFirst);
            isFirst = false;
        }
        return sb.toString();
    }

    private static void sendJson(ServerResponse res, JsonObject object) {
        res.send(JSONP_WRITER.marshall(object));
    }

    private void getMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        MediaType mediaType = findBestAccepted(req.headers());
        if (mediaType == MediaType.APPLICATION_JSON) {
            sendJson(res, toJsonData(registries));
        } else if (mediaType == MediaType.TEXT_PLAIN) {
            res.send(toPrometheusData(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            sendJson(res, toJsonMeta(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        registry.getOptionalMetricWithIDsEntry(metricName)
                .ifPresentOrElse(entry -> {
                    if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
                        JsonObjectBuilder builder = JSON.createObjectBuilder();
                        HelidonMetric.class.cast(entry.getKey()).jsonMeta(builder, entry.getValue());
                        sendJson(res, builder.build());
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
    public static class Builder extends HelidonRestServiceSupport.Builder<MetricsSupport, Builder>
            implements io.helidon.common.Builder<MetricsSupport> {

        private Supplier<RegistryFactory> registryFactory;
        private KeyPerformanceIndicatorMetricsSettings.Builder kpiSettingsBuilder =
                KeyPerformanceIndicatorMetricsSettings.builder();

        protected Builder() {
            super(Builder.class, DEFAULT_CONTEXT);
        }

        @Override
        protected Config webContextConfig(Config config) {
            // align with health checks
            return DeprecatedConfig.get(config, "web-context", "context");

        }

        @Override
        public MetricsSupport build() {
            return build(MetricsSupport::new);
        }

        protected <T extends MetricsSupport> T build(Function<Builder, T> factory) {
            if (null == registryFactory) {
                registryFactory = () -> RegistryFactory.getInstance(config());
            }
            return factory.apply(this);
        }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see KeyPerformanceIndicatorMetricsSettings.Builder Details about key
         * performance metrics configuration
         */
        public Builder config(Config config) {
            super.config(config);
            if (!config.get(BaseRegistry.BASE_ENABLED_KEY).asBoolean().orElse(true)) {
                LOGGER.finest("Metrics support for base metrics is disabled in configuration");
            }
            config.get(KEY_PERFORMANCE_INDICATORS_CONFIG_KEY).ifExists(this::keyPerformanceIndicatorsMetricsConfig);
            return this;
        }

        /**
         * If you want to have multiple registry factories with different
         * endpoints, you may create them using
         * {@link RegistryFactory#create(io.helidon.config.Config)} or
         * {@link RegistryFactory#create()} and create multiple
         * {@link io.helidon.metrics.MetricsSupport} instances with different
         * {@link #webContext(String)} contexts}.
         * <p>
         * If this method is not called,
         * {@link io.helidon.metrics.MetricsSupport} would use the shared
         * instance as provided by
         * {@link io.helidon.metrics.RegistryFactory#getInstance(io.helidon.config.Config)}
         *
         * @param factory factory to use in this metric support
         * @return updated builder instance
         */
        public Builder registryFactory(RegistryFactory factory) {
            registryFactory = () -> factory;
            return this;
        }

        /**
         * Sets the builder for KPI metrics settings, overriding any previously-assigned settings.
         *
         * @param builder for the KPI metrics settings
         * @return updated builder instance
         */
        public Builder keyPerformanceIndicatorsMetricsSettings(KeyPerformanceIndicatorMetricsSettings.Builder builder) {
            this.kpiSettingsBuilder = builder;
            return this;
        }

        /**
         * Updates the KPI metrics config using the extended KPI metrics config node provided.
         *
         * @param kpiConfig Config node containing extended KPI metrics config
         * @return updated builder instance
         */
        public Builder keyPerformanceIndicatorsMetricsConfig(Config kpiConfig) {
            kpiConfig.get(KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(kpiSettingsBuilder::extended);

            Config longRunningRequestsConfig =
                    kpiConfig.get(LONG_RUNNING_REQUESTS_CONFIG_KEY);
            longRunningRequestsConfig.get(LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY)
                    .asLong()
                    .ifPresent(kpiSettingsBuilder::longRunningRequestThresholdMs);

            return this;
        }
    }

    // this class is created for cleaner tracing of web server handlers
    private static final class MetricsContextHandler implements Handler {

        private final Registry appRegistry;
        private final RegistryFactory registryFactory;

        private MetricsContextHandler(Registry appRegistry, RegistryFactory registryFactory) {
            this.appRegistry = appRegistry;
            this.registryFactory = registryFactory;
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            req.context().register(appRegistry);
            req.context().register(registryFactory);
            req.next();
        }
    }

    /**
     * A {@code JsonObjectBuilder} that aggregates, rather than overwrites, when
     * the caller adds objects or arrays with the same name.
     * <p>
     * This builder is tuned to the needs of reporting metrics metadata. Metrics
     * which share the same name but have different tags and have multiple
     * values (called samples) need to appear in the data output as one
     * object with the common name. The name of each sample in the output is
     * decorated with the tags for the sample's parent metric. For example:
     * <p>
     * <pre><code>
     * "carsMeter": {
     * "count;colour=red" : 0,
     * "meanRate;colour=red" : 0,
     * "oneMinRate;colour=red" : 0,
     * "fiveMinRate;colour=red" : 0,
     * "fifteenMinRate;colour=red" : 0,
     * "count;colour=blue" : 0,
     * "meanRate;colour=blue" : 0,
     * "oneMinRate;colour=blue" : 0,
     * "fiveMinRate;colour=blue" : 0,
     * "fifteenMinRate;colour=blue" : 0
     * }
     * </code></pre>
     * <p>
     * The metadata output (as opposed to the data output) must collect tag
     * information from actual instances of the metric under the overall metadata
     * object. This example reflects two instances of the {@code barVal} gauge
     * which have tags of "store" and "component."
     * <pre><code>
     * "barVal": {
     * "unit": "megabytes",
     * "type": "gauge",
     * "tags": [
     *   [
     *     "store=webshop",
     *     "component=backend"
     *   ],
     *   [
     *     "store=webshop",
     *     "component=frontend"
     *   ]
     * ]
     * }
     * </code></pre>
     */
    static final class MergingJsonObjectBuilder implements JsonObjectBuilder {

        private final JsonObjectBuilder delegate;

        private final Map<String, List<JsonObject>> subValuesMap = new HashMap<>();
        private final Map<String, List<JsonArray>> subArraysMap = new HashMap<>();

        MergingJsonObjectBuilder(JsonObjectBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public JsonObjectBuilder add(String name, JsonObjectBuilder subBuilder) {
            final JsonObject ob = subBuilder.build();
            delegate.add(name, JSON.createObjectBuilder(ob));
            List<JsonObject> subValues;
            if (subValuesMap.containsKey(name)) {
                subValues = subValuesMap.get(name);
            } else {
                subValues = new ArrayList<>();
                subValuesMap.put(name, subValues);
            }
            subValues.add(ob);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String name, JsonArrayBuilder arrayBuilder) {
            final JsonArray array = arrayBuilder.build();
            delegate.add(name, JSON.createArrayBuilder(array));
            List<JsonArray> subArrays;
            if (subArraysMap.containsKey(name)) {
                subArrays = subArraysMap.get(name);
            } else {
                subArrays = new ArrayList<>();
                subArraysMap.put(name, subArrays);
            }
            subArrays.add(array);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, JsonValue arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, String arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, BigInteger arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, BigDecimal arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, int arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, long arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, double arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder add(String arg0, boolean arg1) {
            delegate.add(arg0, arg1);
            return this;
        }

        @Override
        public JsonObjectBuilder addNull(String arg0) {
            delegate.addNull(arg0);
            return this;
        }

        @Override
        public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            delegate.addAll(builder);
            return this;
        }

        @Override
        public JsonObjectBuilder remove(String name) {
            delegate.remove(name);
            return this;
        }

        @Override
        public JsonObject build() {
            final JsonObject beforeMerging = delegate.build();
            if (subValuesMap.isEmpty() && subArraysMap.isEmpty()) {
                return beforeMerging;
            }
            final JsonObjectBuilder mainBuilder = JSON.createObjectBuilder(beforeMerging);
            subValuesMap.entrySet().stream()
                    .forEach(entry -> {
                        final JsonObjectBuilder metricBuilder = JSON.createObjectBuilder();
                        for (JsonObject subObject : entry.getValue()) {
                            final JsonObjectBuilder subBuilder = JSON.createObjectBuilder(subObject);
                            metricBuilder.addAll(subBuilder);
                        }
                        mainBuilder.add(entry.getKey(), metricBuilder);
                    });

            subArraysMap.entrySet().stream()
                    .forEach(entry -> {
                        final JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
                        for (JsonArray subArray : entry.getValue()) {
                            final JsonArrayBuilder subArrayBuilder = JSON.createArrayBuilder(subArray);
                            arrayBuilder.add(subArrayBuilder);
                        }
                        mainBuilder.add(entry.getKey(), arrayBuilder);
                    });

            return mainBuilder.build();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
