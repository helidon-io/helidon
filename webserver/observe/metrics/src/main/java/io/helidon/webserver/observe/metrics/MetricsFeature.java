/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.http.HeaderNames.ALLOW;
import static io.helidon.http.Status.METHOD_NOT_ALLOWED_405;
import static io.helidon.http.Status.NOT_FOUND_404;
import static io.helidon.http.Status.OK_200;

class MetricsFeature {

    /**
     * Prefix for key performance indicator metrics names.
     */
    static final String KPI_METER_NAME_PREFIX = "requests";
    private static final String KPI_METER_NAME_PREFIX_WITH_DOT = KPI_METER_NAME_PREFIX + ".";

    private static final Handler DISABLED_ENDPOINT_HANDLER = (req, res) -> res.status(Status.NOT_FOUND_404)
            .send("Metrics are disabled");

    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;
    private KeyPerformanceIndicatorSupport.Metrics kpiMetrics;

    MetricsFeature(MetricsObserverConfig config) {
        this.metricsConfig = config.metricsConfig();
        this.meterRegistry = config.meterRegistry().orElseGet(() -> MetricsFactory.getInstance().globalRegistry(metricsConfig));
    }

    /**
     * Configure Helidon specific metrics.
     *
     * @param rules rules to use
     */
    void configureVendorMetrics(HttpRouting.Builder rules) {
        kpiMetrics =
                KeyPerformanceIndicatorMetricsImpls.get(meterRegistry,
                                                        KPI_METER_NAME_PREFIX_WITH_DOT,
                                                        metricsConfig
                                                                .keyPerformanceIndicatorMetricsConfig(),
                                                        metricsConfig.builtInMeterNameFormat());

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

    void register(HttpRouting.Builder routing, String endpoint) {
        configureVendorMetrics(routing);
        routing.register(endpoint, new MetricsService());
    }

    Optional<?> output(MediaType mediaType,
                       Iterable<String> scopeSelection,
                       Iterable<String> nameSelection) {
        MeterRegistryFormatter formatter = chooseFormatter(meterRegistry,
                                                           mediaType,
                                                           SystemTagsManager.instance().scopeTagName(),
                                                           scopeSelection,
                                                           nameSelection);

        return formatter.format();
    }

    Optional<?> outputMetadata(MediaType mediaType,
                       Iterable<String> scopeSelection,
                       Iterable<String> nameSelection) {
        MeterRegistryFormatter formatter = chooseFormatter(meterRegistry,
                                                           mediaType,
                                                           SystemTagsManager.instance().scopeTagName(),
                                                           scopeSelection,
                                                           nameSelection);

        return formatter.formatMetadata();
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

    private MeterRegistryFormatter chooseFormatter(MeterRegistry meterRegistry,
                                                   MediaType mediaType,
                                                   Optional<String> scopeTagName,
                                                   Iterable<String> scopeSelection,
                                                   Iterable<String> nameSelection) {
        Optional<MeterRegistryFormatter> formatter = HelidonServiceLoader.builder(
                        ServiceLoader.load(MeterRegistryFormatterProvider.class))
                .build()
                .stream()
                .map(provider -> provider.formatter(mediaType,
                                                    metricsConfig,
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
        throw new HttpException("Unsupported media type for metrics formatting: " + mediaType,
                                Status.UNSUPPORTED_MEDIA_TYPE_415,
                                true);
    }

    private void getAll(ServerRequest req, ServerResponse res) {
        getMatching(req, res, req.query().all("scope", List::of), req.query().all("name", List::of));
    }

    private void getMatching(ServerRequest req,
                             ServerResponse res,
                             Iterable<String> scopeSelection,
                             Iterable<String> nameSelection) {
        MediaType mediaType = bestAccepted(req);
        res.header(HeaderValues.CACHE_NO_CACHE);
        if (mediaType == null) {
            res.status(Status.NOT_ACCEPTABLE_406);
            res.send();
            return;
        }

        getOrOptionsMatching(mediaType, res, () -> output(mediaType,
                                                          scopeSelection,
                                                          nameSelection));
    }

    private void getOrOptionsMatching(MediaType mediaType,
                                      ServerResponse res,
                                      Supplier<Optional<?>> dataSupplier) {
        Optional<?> output = dataSupplier.get();

        if (output.isPresent()) {
            res.status(OK_200)
                    .headers().contentType(mediaType);
            res.send(output.get());
        } else {
            res.status(NOT_FOUND_404);
            res.send();
        }
    }

    private void setUpEndpoints(HttpRules rules) {
        if (!metricsConfig.permitAll()) {
            rules.any(SecureHandler.authorize(metricsConfig.roles().toArray(new String[0])));
        }
        // routing to root of metrics
        // As of Helidon 4, this is the only path we should need because scope-based or metric-name-based
        // selection should use query parameters instead of paths.
        rules.get("/", this::getAll)
                .options("/", this::optionsAll);

        // routing to each scope
        // As of Helidon 4, users should use /metrics?scope=xyz instead of /metrics/xyz, and
        // /metrics/?scope=xyz&name=abc instead of /metrics/xyz/abc. These routings are kept
        // temporarily for backward compatibility.

        Meter.Scope.BUILT_IN_SCOPES
                .forEach(scope -> {
                    boolean isScopeEnabled = metricsConfig.isScopeEnabled(scope);
                    rules.get("/" + scope,
                              isScopeEnabled ? (req, res) -> getMatching(req, res, Set.of(scope), Set.of())
                                      : DISABLED_ENDPOINT_HANDLER)
                            .get("/" + scope + "/{metric}",
                                 isScopeEnabled ? (req, res) -> getByName(req, res, Set.of(scope)) // should use ?scope=
                                         : DISABLED_ENDPOINT_HANDLER)
                            .options("/" + scope,
                                     isScopeEnabled ? (req, res) -> optionsMatching(req, res, Set.of(scope), Set.of())
                                             : DISABLED_ENDPOINT_HANDLER)
                            .options("/" + scope + "/{metric}",
                                     isScopeEnabled ? (req, res) -> optionsByName(req, res, Set.of(scope))
                                             : DISABLED_ENDPOINT_HANDLER);
                });
    }

    private void getByName(ServerRequest req, ServerResponse res, Iterable<String> scopeSelection) {
        String metricName = req.path().pathParameters().get("metric");
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
        String metricName = req.path().pathParameters().get("metric");
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

        getOrOptionsMatching(mediaType, res, () -> outputMetadata(mediaType,
                                                                  scopeSelection,
                                                                  nameSelection));
    }

    private void setUpDisabledEndpoints(HttpRules rules) {
        rules.get("/", DISABLED_ENDPOINT_HANDLER)
                .options("/", DISABLED_ENDPOINT_HANDLER);
    }

    /**
     * Separate metrics service class with an afterStop method that is properly invoked.
     */
    private class MetricsService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            if (metricsConfig.enabled()) {
                setUpEndpoints(rules);
            } else {
                setUpDisabledEndpoints(rules);
            }
        }

        @Override
        public void afterStop() {
            kpiMetrics.close();
        }
    }
}
