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

import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.Http;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.http.Http.HeaderNames.ALLOW;
import static io.helidon.http.Http.Status.METHOD_NOT_ALLOWED_405;
import static io.helidon.http.Http.Status.NOT_ACCEPTABLE_406;
import static io.helidon.http.Http.Status.NOT_FOUND_404;
import static io.helidon.http.Http.Status.OK_200;

/**
 * Support for metrics for Helidon Web Server.
 *
 * <p>
 * By defaults creates the /metrics endpoint.
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
// TODO change back to extend HFS
//public class MetricsFeature4 extends HelidonFeatureSupport {
public class MetricsFeature4 extends MetricsFeature {
    private static final System.Logger LOGGER = System.getLogger(MetricsFeature.class.getName());
    private static final Handler DISABLED_ENDPOINT_HANDLER = (req, res) -> res.status(NOT_FOUND_404)
            .send("Metrics are disabled");

    private static final Iterable<String> EMPTY_ITERABLE = Collections::emptyIterator;

    private final MetricsConfig metricsConfig;
    private final MetricsFactory metricsFactory;
    private final MeterRegistry meterRegistry;

    private MetricsFeature4(Builder builder) {
        super(LOGGER, builder, "Metrics");
        this.metricsConfig = builder.metricsConfigBuilder.build();
        this.metricsFactory = builder.metricsFactory.get();
        meterRegistry = Objects.requireNonNullElseGet(builder.meterRegistry, metricsFactory::globalRegistry);
    }

    /**
     * Create an instance to be registered with Web Server with all defaults.
     *
     * @return a new instance built with default values (for context, base
     *         metrics enabled)
     */
    public static MetricsFeature4 create() {
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
    public static MetricsFeature4 create(Config config) {
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
            if (metricsConfig.enabled()) {
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
                                                        metricsConfig
                                                                .keyPerformanceIndicatorMetricsConfig()
                                                                .orElseGet(KeyPerformanceIndicatorMetricsConfig::create));

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
    protected void context(String context) {
        super.context(context);
    }

    @Override
    protected void postSetup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting) {
        configureVendorMetrics(defaultRouting);
        RegistryFactory.getInstance().getRegistry(Registry.BASE_SCOPE); // to trigger lazy creation if it's not already done.
    }

    Optional<?> output(MediaType mediaType,
                       Iterable<String> scopeSelection,
                       Iterable<String> nameSelection) {
        MeterRegistryFormatter formatter = chooseFormatter(meterRegistry,
                                                           mediaType,
                                                           metricsConfig.scopeTagName(),
                                                           scopeSelection,
                                                           nameSelection);

        return formatter.format();
    }

    private MeterRegistryFormatter chooseFormatter(MeterRegistry meterRegistry,
                                                   MediaType mediaType,
                                                   String scopeTagName,
                                                   Iterable<String> scopeSelection,
                                                   Iterable<String> nameSelection) {
        Optional<MeterRegistryFormatter> formatter = HelidonServiceLoader.builder(
                        ServiceLoader.load(MeterRegistryFormatterProvider.class))
                .build()
                .stream()
                .map(provider -> provider.formatter(mediaType,
                                                    meterRegistry,
                                                    scopeTagName,
                                                    scopeSelection,
                                                    nameSelection))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (formatter.isPresent()) {
            return formatter.get();
        }
        throw new UnsupportedOperationException("Unable to find a meter registry formatter for media type " + mediaType);
    }

    private void getAll(ServerRequest req, ServerResponse res) {
        getMatching(req, res, req.query().all("scope", List::of), req.query().all("name", List::of));
    }

    private void getMatching(ServerRequest req,
                             ServerResponse res,
                             Iterable<String> scopeSelection,
                             Iterable<String> nameSelection) {
        MediaType mediaType = bestAccepted(req);
        res.header(Http.Headers.CACHE_NO_CACHE);
        if (mediaType == null) {
            res.status(NOT_ACCEPTABLE_406);
            res.send();
        }

        getOrOptionsMatching(mediaType, res, () -> output(mediaType,
                                                          scopeSelection,
                                                          nameSelection));
    }

    private void getOrOptionsMatching(MediaType mediaType,
                                      ServerResponse res,
                                      Supplier<Optional<?>> dataSupplier) {
        try {
            Optional<?> output = dataSupplier.get();

            if (output.isPresent()) {
                res.status(OK_200)
                        .headers().contentType(mediaType);
                res.send(output.get());
            } else {
                res.status(NOT_FOUND_404);
                res.send();
            }
        } catch (UnsupportedOperationException ex) {
            // We could not find a formatter for that media type from any provider we could locate.
            res.status(NOT_ACCEPTABLE_406);
            res.send();
        } catch (NoClassDefFoundError ex) {
            // Prometheus seems not to be on the path.
            LOGGER.log(Level.DEBUG, "Unable to find Micrometer Prometheus types to scrape the registry");
            res.status(NOT_FOUND_404);
        }
    }

    private static MediaType bestAccepted(ServerRequest req) {
        return req.headers()
                .bestAccepted(MediaTypes.TEXT_PLAIN,
                              MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                              MediaTypes.APPLICATION_JSON)
                .orElse(null);
    }

    private static MediaType bestAcceptedForMetadata(ServerRequest req) {
        return req.headers()
                .bestAccepted(MediaTypes.APPLICATION_JSON)
                .orElse(null);
    }


    private static KeyPerformanceIndicatorSupport.Context kpiContext(ServerRequest request) {
        return request.context()
                .get(KeyPerformanceIndicatorSupport.Context.class)
                .orElseGet(KeyPerformanceIndicatorSupport.Context::create);
    }

    private void setUpEndpoints(HttpRules rules) {
        // routing to root of metrics
        // As of Helidon 4, this is the only path we should need because scope-based or metric-name-based
        // selection should use query parameters instead of paths.
        rules.get("/", this::getAll)
                .options("/", this::optionsAll);

        // routing to each scope
        // As of Helidon 4, users should use /metrics?scope=xyz instead of /metrics/xyz, and
        // /metrics/?scope=xyz&name=abc instead of /metrics/xyz/abc. These routings are kept
        // temporarily for backward compatibility.

        Stream.of(Registry.APPLICATION_SCOPE,
                  Registry.BASE_SCOPE,
                  Registry.VENDOR_SCOPE)
                .forEach(scope -> rules
                        .get("/" + scope, (req, res) -> getMatching(req, res, Set.of(scope), Set.of()))
                        .get("/" + scope + "/{metric}",
                             (req, res) -> getByName(req, res, Set.of(scope))) // should use ?scope=
                        .options("/" + scope, (req, res) -> optionsMatching(req, res, Set.of(scope), Set.of()))
                        .options("/" + scope + "/{metric}", (req, res) -> optionsByName(req, res, Set.of(scope))));
    }

    private void getByName(ServerRequest req, ServerResponse res, Iterable<String> scopeSelection) {
        String metricName = req.path().pathParameters().value("metric");
        getMatching(req, res, scopeSelection, Set.of(metricName));
    }

    private void postRequestProcessing(PostRequestMetricsSupport prms,
                                       ServerRequest request,
                                       ServerResponse response,
                                       Throwable throwable,
                                       KeyPerformanceIndicatorSupport.Context kpiContext) {
        kpiContext.requestProcessingCompleted(throwable == null && response.status().code() < 500);
        prms.runTasks(request, response, throwable);
    }

    private void optionsAll(ServerRequest req, ServerResponse res) {
        optionsMatching(req, res, req.query().all("scope", List::of), req.query().all("name", List::of));
    }

    private void optionsByName(ServerRequest req, ServerResponse res, Iterable<String> scopeSelection) {
        String metricName = req.path().pathParameters().value("metric");
        optionsMatching(req, res, scopeSelection, Set.of(metricName));
    }

    private void optionsMatching(ServerRequest req,
                                 ServerResponse res,
                                 Iterable<String> scopeSelection,
                                 Iterable<String> nameSelection) {
        MediaType mediaType = bestAcceptedForMetadata(req);
        if (mediaType == null) {
            res.header(ALLOW, "GET");
            res.status(METHOD_NOT_ALLOWED_405);
            res.send();
        }

        getOrOptionsMatching(mediaType, res, () -> output(mediaType,
                                                          scopeSelection,
                                                          nameSelection));
    }

    private void setUpDisabledEndpoints(HttpRules rules) {
        rules.get("/", DISABLED_ENDPOINT_HANDLER)
                .options("/", this::optionsAll);

        // routing to GET and OPTIONS for each metrics scope (registry type) and a specific metric within each scope:
        // application, base, vendor
        Registry.BUILT_IN_SCOPES
                .forEach(type -> Stream.of("", "/{metric}") // for the whole scope and for a specific metric within that scope
                        .map(suffix -> "/" + type + suffix)
                        .forEach(path -> rules.get(path, DISABLED_ENDPOINT_HANDLER)
                                .options(path, this::optionsAll)
                        ));
    }

    /**
     * A fluent API builder to build instances of {@link MetricsFeature}.
     */
    // TODO change back to normal extends clause
//    public static final class Builder extends HelidonFeatureSupport.Builder<Builder, MetricsFeature4> {
    public static final class Builder extends MetricsFeature.Builder {
        private LazyValue<MetricsFactory> metricsFactory;
        private MeterRegistry meterRegistry;
        private MetricsConfig.Builder metricsConfigBuilder = MetricsConfig.builder();

        private Builder() {
            super("metrics");
        }

        @Override
        public MetricsFeature4 build() {
            metricsFactory = Objects.requireNonNullElseGet(metricsFactory,
                                                           () -> LazyValue.create(() -> MetricsFactory.getInstance(
                                                                   metricsConfigBuilder.build())));
            return new MetricsFeature4(this);
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
            metricsConfigBuilder.config(config);
            return this;
        }

        /**
         * Assigns {@link io.helidon.metrics.api.MetricsConfig} which will be used in creating the instance at build-time.
         *
         * @param metricsConfigBuilder the metrics config to assign for use in building the instance
         * @return updated builder
         */
        @ConfiguredOption(mergeWithParent = true,
                          type = MetricsConfig.class)
        public Builder metricsConfig(MetricsConfig.Builder metricsConfigBuilder) {
            this.metricsConfigBuilder = metricsConfigBuilder;
            return this;
        }

        /**
         * Assigns the {@link io.helidon.metrics.api.MeterRegistry} to query for formatting output.
         *
         * @param meterRegistry the meter registry to use
         * @return updated builder
         */
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }
    }
}
