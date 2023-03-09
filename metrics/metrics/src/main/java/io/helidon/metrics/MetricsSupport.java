/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.serviceapi.MinimalMetricsSupport;
import io.helidon.metrics.serviceapi.PostRequestMetricsSupport;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.servicecommon.rest.RestServiceSettings;
import io.helidon.webserver.Handler;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;


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
public final class MetricsSupport extends HelidonRestServiceSupport
        implements io.helidon.metrics.serviceapi.MetricsSupport {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final String SERVICE_NAME = "Metrics";

    private static final MessageBodyWriter<JsonStructure> JSONP_WRITER = JsonpSupport.writer();

    private final RegistryFactory rf;

    private final MetricsSettings metricsSettings;

    private static final Logger LOGGER = Logger.getLogger(MetricsSupport.class.getName());

    /**
     * Creates a new {@code  MetricsSupport} instance from the provided builder.
     *
     * @param builder the builder for preparing the new instance
     */
    protected MetricsSupport(Builder builder) {
        super(LOGGER, builder, SERVICE_NAME);
        this.rf = builder.registryFactory.get();
        this.metricsSettings = builder.metricsSettingsBuilder.build();
        SystemTagsManager.create(metricsSettings);
    }

    /**
     * Creates a new {@code MetricsSupport} instance from the provides settings.
     *
     * @param metricsSettings the metrics settings to use in preparing the {@code MetricsSupport} instance
     * @param restServiceSettings rest services settings to use in preparing the {@code MetricsSupport} instance
     */
    protected MetricsSupport(MetricsSettings metricsSettings, RestServiceSettings restServiceSettings) {
        super(LOGGER, restServiceSettings, SERVICE_NAME);
        rf = RegistryFactory.getInstance(metricsSettings);
        this.metricsSettings = metricsSettings;
        SystemTagsManager.create(metricsSettings);
    }

    /**
     * Create an instance to be registered with Web Server with all defaults.
     *
     * @return a new instance built with default values (for context, base
     * metrics enabled)
     */
    public static MetricsSupport create() {
        return (MetricsSupport) io.helidon.metrics.serviceapi.MetricsSupport.create();
    }

    /**
     * Create an instance to be registered with Web Server with the specific metrics settings.
     *
     * @param metricsSettings metrics settings to use for initializing metrics
     * @param restServiceSettings REST service settings for managing the endpoint
     *
     * @return a new instance built with the specified metrics settings
     */
    public static MetricsSupport create(MetricsSettings metricsSettings, RestServiceSettings restServiceSettings) {
        return (MetricsSupport) io.helidon.metrics.serviceapi.MetricsSupport.create(metricsSettings, restServiceSettings);
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
        return create(MetricsSettings.create(config),
                      io.helidon.metrics.serviceapi.MetricsSupport.defaultedMetricsRestServiceSettingsBuilder()
                              .config(config)
                              .build());
    }

    static JsonObjectBuilder createMergingJsonObjectBuilder(JsonObjectBuilder delegate) {
        return new MergingJsonObjectBuilder(delegate);
    }

    // For testing
    KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorMetricsConfig() {
        return metricsSettings.keyPerformanceIndicatorSettings();
    }

    /**
     * Create a new builder to construct an instance.
     *
     * @return A new builder instance
     * @deprecated Use {@link io.helidon.metrics.serviceapi.MetricsSupport#builder()} instead.
     */
    @Deprecated(since = "2.5.2", forRemoval = true)
    public static Builder builder() {
        return new Builder();
    }

    private static MediaType findBestAccepted(RequestHeaders headers) {
        Optional<MediaType> mediaType = headers.bestAccepted(MediaType.TEXT_PLAIN,
                                                             MediaType.APPLICATION_JSON,
                                                             MediaType.APPLICATION_OPENMETRICS);
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
        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        MediaType mediaType = findBestAccepted(req.headers());
        if (matches(mediaType, MediaType.APPLICATION_JSON)) {
            sendJson(res, toJsonData(registry));
        } else if (matches(mediaType, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OPENMETRICS)) {
            sendPrometheus(res, toPrometheusData(registry), mediaType);
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

        // Options returns only the metadata, so it's OK to allow caching.
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
                        toPrometheusData(builder,
                                         entry.getKey(),
                                         entry.getValue(),
                                         true,
                                         registry.registrySettings().isStrictExemplars());
                        serialized.add(name);
                    } else {
                        toPrometheusData(builder,
                                         entry.getKey(),
                                         entry.getValue(),
                                         false,
                                         registry.registrySettings().isStrictExemplars());
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
        return toPrometheusData(metricID, metric, withHelpType, true);
    }

    static String toPrometheusData(MetricID metricID, Metric metric, boolean withHelpType, boolean isStrictExemplars) {
        final StringBuilder sb = new StringBuilder();
        checkMetricTypeThenRun(sb, metricID, metric, withHelpType, isStrictExemplars);
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
     * @param isStrictExemplars whether to use strict exemplar support
     */
    static void toPrometheusData(StringBuilder sb,
                                 MetricID metricID,
                                 Metric metric,
                                 boolean withHelpType,
                                 boolean isStrictExemplars) {
        checkMetricTypeThenRun(sb, metricID, metric, withHelpType, isStrictExemplars);
    }

    private static void checkMetricTypeThenRun(StringBuilder sb,
                                               MetricID metricID,
                                               Metric metric,
                                               boolean withHelpType,
                                               boolean isStrictExemplars) {
        Objects.requireNonNull(metric);

        if (!(metric instanceof HelidonMetric)) {
            throw new IllegalArgumentException(String.format(
                    "Metric of type %s is expected to implement %s but does not",
                    metric.getClass().getName(),
                    HelidonMetric.class.getName()));
        }

        ((HelidonMetric) metric).prometheusData(sb, metricID, withHelpType, isStrictExemplars);
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
    @Override
    public void configureVendorMetrics(String routingName,
                                       Routing.Rules rules) {
        String metricPrefix = metricsNamePrefix(routingName);

        KeyPerformanceIndicatorSupport.Metrics kpiMetrics =
                KeyPerformanceIndicatorMetricsImpls.get(metricPrefix,
                                                        metricsSettings
                                                                .keyPerformanceIndicatorSettings());

        rules.any((req, res) -> {
            KeyPerformanceIndicatorSupport.Context kpiContext = kpiContext(req);
            PostRequestMetricsSupport prms = PostRequestMetricsSupport.create();
            req.context().register(prms);

            kpiContext.requestHandlingStarted(kpiMetrics);
            res.whenSent()
                    // Perform updates which depend on completion of request *processing* (after the response is sent).
                    .thenAccept(r -> postRequestProcessing(prms, req, r, null, kpiContext))
                    .exceptionallyAccept(t -> postRequestProcessing(prms, req, res, t, kpiContext));
            Exception exception = null;
            try {
                req.next();
            } catch (Exception e) {
                exception = e;
                throw e;
            } finally {
                // Perform updates which depend on completion of request *handling* (after the server has begun request
                // *processing* but, in the case of async requests, possibly before processing has finished).
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
        // If metrics are disabled, the RegistryFactory will be the no-op, not the full-featured one.
        if (rf instanceof io.helidon.metrics.RegistryFactory) {
            io.helidon.metrics.RegistryFactory fullRF = (io.helidon.metrics.RegistryFactory) rf;
            Registry app = fullRF.getARegistry(MetricRegistry.Type.APPLICATION);

            PeriodicExecutor.start();

            // register the metric registry and factory to be available to all
            MetricsContextHandler metricsContextHandler = new MetricsContextHandler(app, rf);
            defaultRules.any(metricsContextHandler);
            if (defaultRules != serviceEndpointRoutingRules) {
                serviceEndpointRoutingRules.any(metricsContextHandler);
            }

            configureVendorMetrics(null, defaultRules);
        }
        prepareMetricsEndpoints(context(), serviceEndpointRoutingRules);
    }

    @Override
    public void prepareMetricsEndpoints(String endpointContext, Routing.Rules serviceEndpointRoutingRules) {
        if (rf instanceof io.helidon.metrics.RegistryFactory) {
            setUpFullFeaturedEndpoint(serviceEndpointRoutingRules, (io.helidon.metrics.RegistryFactory) rf);
        } else {
            MinimalMetricsSupport.createEndpointForDisabledMetrics(endpointContext, serviceEndpointRoutingRules);
        }
    }

    private void setUpFullFeaturedEndpoint(Routing.Rules serviceEndpointRoutingRules,
                                           io.helidon.metrics.RegistryFactory rf) {
        Registry base = rf.getARegistry(MetricRegistry.Type.BASE);
        Registry vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        Registry app = rf.getARegistry(MetricRegistry.Type.APPLICATION);
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

    private void postRequestProcessing(PostRequestMetricsSupport prms,
                                       ServerRequest request,
                                       ServerResponse response,
                                       Throwable throwable,
                                       KeyPerformanceIndicatorSupport.Context kpiContext) {
        kpiContext.requestProcessingCompleted(throwable == null && response.status().code() < 500);
        prms.runTasks(request, response, throwable);
    }

    private void getByName(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        registry.getOptionalMetricEntry(metricName)
                .ifPresentOrElse(entry -> {
                    MediaType mediaType = findBestAccepted(req.headers());
                    if (matches(mediaType, MediaType.APPLICATION_JSON)) {
                        sendJson(res, jsonDataByName(registry, metricName));
                    } else if (matches(mediaType, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OPENMETRICS)) {
                        sendPrometheus(res, prometheusDataByName(registry, metricName), mediaType);
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
            HelidonMetric metric = metricEntry.getValue();
            if (registry.isMetricEnabled(metricName)) {
                metric.jsonData(builder, metricEntry.getKey());
            }
        }
        return builder.build();
    }

    static String prometheusDataByName(Registry registry, String metricName) {
        final StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (Map.Entry<MetricID, HelidonMetric> metricEntry : registry.getMetricsByName(metricName)) {
            HelidonMetric metric = metricEntry.getValue();
            if (registry.isMetricEnabled(metricName)) {
                metric.prometheusData(sb, metricEntry.getKey(), isFirst, registry.registrySettings().isStrictExemplars());
            }
            isFirst = false;
        }
        return sb.toString();
    }

    private static void sendJson(ServerResponse res, JsonObject object) {
        res.send(JSONP_WRITER.marshall(object));
    }

    private void getMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        MediaType mediaType = findBestAccepted(req.headers());
        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        if (matches(mediaType, MediaType.APPLICATION_JSON)) {
            sendJson(res, toJsonData(registries));
        } else if (matches(mediaType, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OPENMETRICS)) {
            sendPrometheus(res, toPrometheusData(registries), mediaType);
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        // Options returns metadata only, so do not discourage caching.
        if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
            sendJson(res, toJsonMeta(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private static boolean matches(MediaType candidateMediaType, MediaType... standardTypes) {
        for (MediaType mt : standardTypes) {
            if (mt.test(candidateMediaType)) {
                return true;
            }
        }
        return false;
    }

    private void optionsOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        Optional.ofNullable(registry.metadataWithIDs(metricName))
                .ifPresentOrElse(entry -> {
                    // Options returns only metadata, so do not discourage caching.
                    if (req.headers().isAccepted(MediaType.APPLICATION_JSON)) {
                        JsonObjectBuilder builder = JSON.createObjectBuilder();
                        // The returned list of metric IDs is guaranteed to have at least one element at this point.
                        // Use the first to find a metric which will know how to create the metadata output.
                        HelidonMetric.class.cast(registry.getMetric(entry.getValue().get(0))).jsonMeta(builder, entry.getValue());
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

    private static void sendPrometheus(ServerResponse res, String formattedOutput, MediaType requestedMediaType) {
        MediaType.Builder responseMediaTypeBuilder = MediaType.builder()
                .type(requestedMediaType.type())
                .subtype(requestedMediaType.subtype())
                .charset("UTF-8");

        if (matches(requestedMediaType, MediaType.APPLICATION_OPENMETRICS)) {
            responseMediaTypeBuilder.addParameter("version", "1.0.0");
        } else if (matches(requestedMediaType, MediaType.TEXT_PLAIN)) {
            responseMediaTypeBuilder.addParameter("version", "0.0.4");
        }
        res.addHeader("Content-Type", responseMediaTypeBuilder.build().toString());
        res.send(formattedOutput + "# EOF\n");
    }

    /**
     * A fluent API builder to build instances of {@link MetricsSupport}.
     */
    public static class Builder extends HelidonRestServiceSupport.Builder<Builder, MetricsSupport>
            implements io.helidon.metrics.serviceapi.MetricsSupport.Builder<Builder, MetricsSupport> {

        private Supplier<RegistryFactory> registryFactory;
        private MetricsSettings.Builder metricsSettingsBuilder = MetricsSettings.builder();

        /**
         * Creates a new builder instance.
         */
        protected Builder() {
            super(MetricsSettings.Builder.DEFAULT_CONTEXT);
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

        /**
         * Creates a new {@code MetricsSupport} instance from the provided factory.
         *
         * @param factory the factory which maps the builder to a {@code MetricsSupport} instance
         * @return the created {@code MetricsSupport} instance
         */
        protected MetricsSupport build(Function<Builder, MetricsSupport> factory) {
            if (null == registryFactory) {
                registryFactory = () -> RegistryFactory.getInstance(MetricsSettings.create(config()));
            }
            MetricsSupport result = factory.apply(this);
            if (!result.metricsSettings.baseMetricsSettings().isEnabled()) {
                LOGGER.finest("Metrics support for base metrics is disabled in settings");
            }

            return result;
        }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see KeyPerformanceIndicatorMetricsSettings.Builder Details about key
         * performance metrics configuration
         * @deprecated Use {@link #metricsSettings(MetricsSettings.Builder)} instead
         */
        @Deprecated(since = "2.4.0", forRemoval = true)
        public Builder config(Config config) {
            super.config(config);
            metricsSettingsBuilder.config(config);
            return this;
        }

        @Override
        public Builder metricsSettings(MetricsSettings.Builder metricsSettingsBuilder) {
            this.metricsSettingsBuilder = metricsSettingsBuilder;
            return this;
        }

        /**
         * If you want to have multiple registry factories with different
         * endpoints, you may create them using
         * {@link RegistryFactory#create(MetricsSettings)} or
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
         * @deprecated Use {@link #metricsSettings(MetricsSettings.Builder)} with
         * {@link MetricsSettings.Builder#keyPerformanceIndicatorSettings(KeyPerformanceIndicatorMetricsSettings.Builder)}
         * instead.
         */
        @Deprecated(since = "2.4.0", forRemoval = true)
        public Builder keyPerformanceIndicatorsMetricsSettings(KeyPerformanceIndicatorMetricsSettings.Builder builder) {
            this.metricsSettingsBuilder.keyPerformanceIndicatorSettings(builder);
            return this;
        }

        /**
         * Updates the KPI metrics config using the extended KPI metrics config node provided.
         *
         * @param kpiConfig Config node containing extended KPI metrics config
         * @return updated builder instance
         * @deprecated Use {@link #metricsSettings(MetricsSettings.Builder)} with
         * {@link MetricsSettings.Builder#keyPerformanceIndicatorSettings(KeyPerformanceIndicatorMetricsSettings.Builder)}
         * instead.
         */
        @Deprecated(since = "2.4.0", forRemoval = true)
        public Builder keyPerformanceIndicatorsMetricsConfig(Config kpiConfig) {
            return keyPerformanceIndicatorsMetricsSettings(
                    KeyPerformanceIndicatorMetricsSettings.builder().config(kpiConfig));
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
            if (Double.isNaN(arg1)) {
                delegate.add(arg0, String.valueOf(Double.NaN));
            } else {
                delegate.add(arg0, arg1);
            }
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
