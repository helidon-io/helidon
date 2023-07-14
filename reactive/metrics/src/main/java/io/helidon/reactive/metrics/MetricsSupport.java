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

package io.helidon.reactive.metrics;

import java.util.Collections;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.serviceapi.JsonFormat;
import io.helidon.metrics.serviceapi.PrometheusFormat;
import io.helidon.reactive.media.common.MessageBodyWriter;
import io.helidon.reactive.media.jsonp.JsonpSupport;
import io.helidon.reactive.servicecommon.HelidonRestServiceSupport;
import io.helidon.reactive.webserver.Handler;
import io.helidon.reactive.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import org.eclipse.microprofile.metrics.MetricID;

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
 * {@link #create(io.helidon.config.Config)}. The following configuration parameters can be used:
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
public class MetricsSupport extends HelidonRestServiceSupport {
    private static final Logger LOGGER = Logger.getLogger(MetricsSupport.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final Handler DISABLED_ENDPOINT_HANDLER = (req, res) -> res.status(Http.Status.NOT_FOUND_404)
            .send("Metrics are disabled");
    private static final MessageBodyWriter<JsonStructure> JSONP_WRITER = JsonpSupport.writer();

    private final MetricsSettings metricsSettings;
    private final RegistryFactory registryFactory;

    private MetricsSupport(Builder builder) {
        super(LOGGER, builder, "Metrics");

        this.registryFactory = builder.registryFactory();
        this.metricsSettings = builder.metricsSettings();
        SystemTagsManager.create(metricsSettings);
    }

    /**
     * Create an instance to be registered with Web Server with all defaults.
     *
     * @return a new instance built with default values (for context, base
     *         metrics enabled)
     */
    public static MetricsSupport create() {
        return builder().build();
    }

    /**
     * Create an instance to be registered with Web Server maybe overriding
     * default values with configured values.
     *
     * @param config Config instance to use to (maybe) override configuration of
     *               this component. See class javadoc for supported configuration keys.
     * @return a new instance configured withe config provided
     */
    public static MetricsSupport create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new builder to construct an instance.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules, rules);
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        if (registryFactory.enabled()) {
            registryFactory.start();
            configureVendorMetrics(defaultRules);
            setUpEndpoints(context(), serviceEndpointRoutingRules);
        } else {
            setUpDisabledEndpoints(context(), serviceEndpointRoutingRules);
        }
    }

    @Override
    protected void onShutdown() {
        if (registryFactory.enabled()) {
            registryFactory.stop();
        }
    }

    private static KeyPerformanceIndicatorSupport.Context kpiContext(ServerRequest request) {
        return request.context()
                .get(KeyPerformanceIndicatorSupport.Context.class)
                .orElseGet(KeyPerformanceIndicatorSupport.Context::create);
    }

    private static void getAll(ServerRequest req, ServerResponse res, Registry registry) {
        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        MediaType mediaType = bestAccepted(req);

        if (mediaType == MediaTypes.APPLICATION_JSON) {
            sendJson(res, JsonFormat.jsonData(registry));
        } else if (mediaType == MediaTypes.TEXT_PLAIN) {
            res.send(PrometheusFormat.prometheusData(registry));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private static MediaType bestAccepted(ServerRequest req) {
        return req.headers()
                .bestAccepted(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_JSON)
                .orElse(null);
    }

    private static void sendJson(ServerResponse res, JsonObject object) {
        res.send(JSONP_WRITER.marshall(object));
    }

    private void setUpEndpoints(String context, Routing.Rules rules) {
        Registry base = registryFactory.getRegistry(Registry.BASE_SCOPE);
        Registry vendor = registryFactory.getRegistry(Registry.VENDOR_SCOPE);
        Registry app = registryFactory.getRegistry(Registry.APPLICATION_SCOPE);

        // routing to root of metrics
        rules.get(context, (req, res) -> getMultiple(req, res, base, app, vendor))
                .options(context, (req, res) -> optionsMultiple(req, res, base, app, vendor));

        // routing to each scope
        Stream.of(app, base, vendor)
                .forEach(registry -> {
                    String type = registry.scope();

                    rules.get(context + "/" + type, (req, res) -> getAll(req, res, registry))
                            .get(context + "/" + type + "/{metric}", (req, res) -> getByName(req, res, registry))
                            .options(context + "/" + type, (req, res) -> optionsAll(req, res, registry))
                            .options(context + "/" + type + "/{metric}", (req, res) -> optionsOne(req, res, registry));
                });
    }

    private void getByName(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        registry.find(metricName)
                .ifPresentOrElse(entry -> {
                    MediaType mediaType = bestAccepted(req);
                    if (mediaType == MediaTypes.APPLICATION_JSON) {
                        sendJson(res, JsonFormat.jsonDataByName(registry, metricName));
                    } else if (mediaType == MediaTypes.TEXT_PLAIN) {
                        res.send(PrometheusFormat.prometheusDataByName(registry, metricName));
                    } else {
                        res.status(Http.Status.NOT_ACCEPTABLE_406);
                        res.send();
                    }
                }, () -> {
                    res.status(Http.Status.NOT_FOUND_404);
                    res.send();
                });
    }

    private void optionsAll(ServerRequest req, ServerResponse res, Registry registry) {
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        // Options returns only the metadata, so it's OK to allow caching.
        if (req.headers().isAccepted(MediaTypes.APPLICATION_JSON)) {
            sendJson(res, JsonFormat.jsonMeta(registry));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }

    }

    private void configureVendorMetrics(Routing.Rules rules) {
        String metricPrefix = "requests.";

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

    private void getMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        MediaType mediaType = bestAccepted(req);
        res.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);
        if (mediaType == MediaTypes.APPLICATION_JSON) {
            sendJson(res, JsonFormat.jsonData(registries));
        } else if (mediaType == MediaTypes.TEXT_PLAIN) {
            res.send(PrometheusFormat.prometheusData(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        // Options returns metadata only, so do not discourage caching.
        if (req.headers().isAccepted(MediaTypes.APPLICATION_JSON)) {
            sendJson(res, JsonFormat.jsonMeta(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().param("metric");

        registry.metricsByName(metricName)
                .ifPresentOrElse(entry -> {
                    // Options returns only metadata, so do not discourage caching.
                    if (req.headers().isAccepted(MediaTypes.APPLICATION_JSON)) {
                        JsonObjectBuilder builder = JSON.createObjectBuilder();
                        // The returned list of metric IDs is guaranteed to have at least one element at this point.
                        // Use the first to find a metric which will know how to create the metadata output.
                        MetricID metricId = entry.metricIds().get(0);
                        JsonFormat.jsonMeta(builder, registry.getMetric(metricId), entry.metricIds());
                        sendJson(res, builder.build());
                    } else {
                        res.status(Http.Status.NOT_ACCEPTABLE_406).send();
                    }
                }, () -> res.status(Http.Status.NOT_FOUND_404).send()); // metric not found
    }

    private void postRequestProcessing(PostRequestMetricsSupport prms,
                                       ServerRequest request,
                                       ServerResponse response,
                                       Throwable throwable,
                                       KeyPerformanceIndicatorSupport.Context kpiContext) {
        kpiContext.requestProcessingCompleted(throwable == null && response.status().code() < 500);
        prms.runTasks(request, response, throwable);
    }

    private void setUpDisabledEndpoints(String context, Routing.Rules rules) {
        rules.get(context, DISABLED_ENDPOINT_HANDLER)
                .options(context, DISABLED_ENDPOINT_HANDLER);

        // routing to GET and OPTIONS for each metrics scope (registry type) and a specific metric within each scope:
        // application, base, vendor
       Registry.BUILT_IN_SCOPES
                .forEach(type -> Stream.of("", "/{metric}") // for the whole scope and for a specific metric within that scope
                        .map(suffix -> context + "/" + type + suffix)
                        .forEach(path -> rules.get(path, DISABLED_ENDPOINT_HANDLER)
                                .options(path, DISABLED_ENDPOINT_HANDLER)
                        ));
    }

    /**
     * A fluent API builder to build instances of {@link MetricsSupport}.
     */
    public static final class Builder extends HelidonRestServiceSupport.Builder<Builder, MetricsSupport> {
        private LazyValue<RegistryFactory> registryFactory;
        private MetricsSettings.Builder metricsSettingsBuilder = MetricsSettings.builder();

        private Builder() {
            super("/metrics");
        }

        @Override
        public MetricsSupport build() {
            if (registryFactory == null) {
                registryFactory = LazyValue.create(() -> RegistryFactory.getInstance(metricsSettingsBuilder.build()));
            }
            return new MetricsSupport(this);
        }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder Details about key
         *         performance metrics configuration
         */
        public Builder config(Config config) {
            super.config(config);
            metricsSettingsBuilder.config(config);
            return this;
        }

        /**
         * Assigns {@code MetricsSettings} which will be used in creating the {@code MetricsSupport} instance at build-time.
         *
         * @param metricsSettingsBuilder the metrics settings to assign for use in building the {@code MetricsSupport} instance
         * @return updated builder
         */
        @ConfiguredOption(mergeWithParent = true,
                          type = MetricsSettings.class)
        public Builder metricsSettings(MetricsSettings.Builder metricsSettingsBuilder) {
            this.metricsSettingsBuilder = metricsSettingsBuilder;
            return this;
        }

        /**
         * If you want to have multiple registry factories with different
         * endpoints, you may create them using
         * {@link RegistryFactory#create(MetricsSettings)} or
         * {@link RegistryFactory#create()} and create multiple
         * {@link io.helidon.reactive.metrics.MetricsSupport} instances with different
         * {@link #webContext(String)} contexts}.
         * <p>
         * If this method is not called,
         * {@link io.helidon.reactive.metrics.MetricsSupport} would use the shared
         * instance as provided by
         * {@link io.helidon.metrics.api.RegistryFactory#getInstance(io.helidon.config.Config)}
         *
         * @param factory factory to use in this metric support
         * @return updated builder instance
         */
        public Builder registryFactory(RegistryFactory factory) {
            registryFactory = LazyValue.create(() -> factory);
            return this;
        }

        RegistryFactory registryFactory() {
            return registryFactory.get();
        }

        MetricsSettings metricsSettings() {
            return metricsSettingsBuilder.build();
        }
    }
}
