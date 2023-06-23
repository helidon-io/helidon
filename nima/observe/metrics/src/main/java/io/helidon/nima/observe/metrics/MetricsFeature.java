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
package io.helidon.nima.observe.metrics;

import java.util.Collections;
import java.util.Optional;
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
import io.helidon.metrics.serviceapi.PrometheusFormat;
import io.helidon.nima.servicecommon.HelidonFeatureSupport;
import io.helidon.nima.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

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
public class MetricsFeature extends HelidonFeatureSupport {
    private static final System.Logger LOGGER = System.getLogger(MetricsFeature.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final Handler DISABLED_ENDPOINT_HANDLER = (req, res) -> res.status(Http.Status.NOT_FOUND_404)
            .send("Metrics are disabled");

    private final MetricsSettings metricsSettings;
    private final RegistryFactory registryFactory;

    private MetricsFeature(Builder builder) {
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
    public static MetricsFeature create() {
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
    public static MetricsFeature create(Config config) {
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
    public Optional<HttpService> service() {
        // main service is responsible for exposing metrics endpoints over HTTP
        return Optional.of(rules -> {
            if (registryFactory.enabled()) {
                setUpEndpoints(rules);
            } else {
                setUpDisabledEndpoints(rules);
            }
        });
    }

    /**
     * Configure Helidon specific metrics.
     *
     * @param rules     rules to use
     */
    public void configureVendorMetrics(HttpRouting.Builder rules) {
        String metricPrefix = "requests.";

        KeyPerformanceIndicatorSupport.Metrics kpiMetrics =
                KeyPerformanceIndicatorMetricsImpls.get(metricPrefix,
                                                        metricsSettings
                                                                .keyPerformanceIndicatorSettings());

        rules.addFilter((chain, req, res) -> {
            KeyPerformanceIndicatorSupport.Context kpiContext = kpiContext(req);
            PostRequestMetricsSupport prms = PostRequestMetricsSupport.create();
            req.context().register(prms);

            kpiContext.requestHandlingStarted(kpiMetrics);
            try {
                chain.proceed();
                postRequestProcessing(prms, req, res, null, kpiContext);
            } catch (Exception e) {
                postRequestProcessing(prms, req, res, e, kpiContext);
            }
        });
    }

    @Override
    public void beforeStart() {
        if (registryFactory.enabled()) {
            registryFactory.start();
        }
    }

    @Override
    public void afterStop() {
        if (registryFactory.enabled()) {
            registryFactory.stop();
        }
    }

    @Override
    protected void context(String context) {
        super.context(context);
    }

    @Override
    protected void postSetup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting) {
        configureVendorMetrics(defaultRouting);
    }

    private static void getAll(ServerRequest req, ServerResponse res, Registry registry) {
        res.header(Http.HeaderValues.CACHE_NO_CACHE);
        if (registry.empty()) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
            return;
        }

        MediaType mediaType = bestAccepted(req);

        if (mediaType == MediaTypes.TEXT_PLAIN) {
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
        res.send(object);
    }

    private static KeyPerformanceIndicatorSupport.Context kpiContext(ServerRequest request) {
        return request.context()
                .get(KeyPerformanceIndicatorSupport.Context.class)
                .orElseGet(KeyPerformanceIndicatorSupport.Context::create);
    }

    private void setUpEndpoints(HttpRules rules) {
        Registry base = registryFactory.getRegistry(Registry.BASE_SCOPE);
        Registry vendor = registryFactory.getRegistry(Registry.VENDOR_SCOPE);
        Registry app = registryFactory.getRegistry(Registry.APPLICATION_SCOPE);

        // routing to root of metrics
        rules.get("/", (req, res) -> getMultiple(req, res, base, app, vendor))
                .options("/", (req, res) -> optionsMultiple(req, res, base, app, vendor));

        // routing to each scope
        Stream.of(app, base, vendor)
                .forEach(registry -> {
                    String type = registry.scope();

                    rules.get("/" + type, (req, res) -> getAll(req, res, registry))
                            .get("/" + type + "/{metric}", (req, res) -> getByName(req, res, registry))
                            .options("/" + type, (req, res) -> optionsAll(req, res, registry))
                            .options("/" + type + "/{metric}", (req, res) -> optionsOne(req, res, registry));
                });
    }

    private void getByName(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().pathParameters().value("metric");

        res.header(Http.HeaderValues.CACHE_NO_CACHE);
        registry.find(metricName)
                .ifPresentOrElse(entry -> {
                    MediaType mediaType = bestAccepted(req);
                    if (mediaType == MediaTypes.TEXT_PLAIN) {
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

        // Options used to provide metadata for JSON output, but it's not used for Prometheus.
        res.status(Http.Status.NOT_ACCEPTABLE_406);
        res.send();

    }

    private void postRequestProcessing(PostRequestMetricsSupport prms,
                                       ServerRequest request,
                                       ServerResponse response,
                                       Throwable throwable,
                                       KeyPerformanceIndicatorSupport.Context kpiContext) {
        kpiContext.requestProcessingCompleted(throwable == null && response.status().code() < 500);
        prms.runTasks(request, response, throwable);
    }

    private void getMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        MediaType mediaType = bestAccepted(req);
        res.header(Http.HeaderValues.CACHE_NO_CACHE);
        if (mediaType == MediaTypes.TEXT_PLAIN) {
            res.send(PrometheusFormat.prometheusData(registries));
        } else {
            res.status(Http.Status.NOT_ACCEPTABLE_406);
            res.send();
        }
    }

    private void optionsMultiple(ServerRequest req, ServerResponse res, Registry... registries) {
        // Options used to return metadata but it's no longer supported unless we restore JSON support.
        res.status(Http.Status.NOT_ACCEPTABLE_406);
        res.send();
    }

    private void optionsOne(ServerRequest req, ServerResponse res, Registry registry) {
        String metricName = req.path().pathParameters().value("metric");

        registry.metricsByName(metricName)
                .ifPresentOrElse(entry -> {
                    res.status(Http.Status.NOT_ACCEPTABLE_406).send();
                }, () -> res.status(Http.Status.NOT_FOUND_404).send()); // metric not found
    }

    private void setUpDisabledEndpoints(HttpRules rules) {
        rules.get("/", DISABLED_ENDPOINT_HANDLER)
                .options("/", DISABLED_ENDPOINT_HANDLER);

        // routing to GET and OPTIONS for each metrics scope (registry type) and a specific metric within each scope:
        // application, base, vendor
        Registry.BUILT_IN_SCOPES
                .forEach(type -> Stream.of("", "/{metric}") // for the whole scope and for a specific metric within that scope
                        .map(suffix -> "/" + type + suffix)
                        .forEach(path -> rules.get(path, DISABLED_ENDPOINT_HANDLER)
                                .options(path, DISABLED_ENDPOINT_HANDLER)
                        ));
    }

    /**
     * A fluent API builder to build instances of {@link MetricsFeature}.
     */
    public static final class Builder extends HelidonFeatureSupport.Builder<Builder, MetricsFeature> {
        private LazyValue<RegistryFactory> registryFactory;
        private MetricsSettings.Builder metricsSettingsBuilder = MetricsSettings.builder();

        private Builder() {
            super("metrics");
        }

        @Override
        public MetricsFeature build() {
            if (registryFactory == null) {
                registryFactory = LazyValue.create(() -> RegistryFactory.getInstance(metricsSettingsBuilder.build()));
            }
            return new MetricsFeature(this);
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
         * {@link MetricsFeature} instances with different
         * {@link #webContext(String)} contexts}.
         * <p>
         * If this method is not called,
         * {@link MetricsFeature} would use the shared
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
