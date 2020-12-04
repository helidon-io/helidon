/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics.micrometer;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import static io.helidon.webserver.cors.CorsEnabledServiceHelper.CORS_CONFIG_KEY;

/**
 * Implements simple Micrometer support.
 * <p>
 * Developers create {@code MeterRegistry} objects and enroll them with
 * {@code }MicrometerSupport}, and also provide a {@code Handler} for expressing the registry's data in an HTTP response.
 * </p>
 * <p>Alternatively, developers can register any of the built-in registries exposed by the builder as
 * the {@link Builder.BuiltInRegistry} enum.</p>
 */
public class MicrometerSupport implements Service {

    /**
     * Config key for specifying built-in registry names to enroll.
     */
    public static final String BUILTIN_REGISTRIES_CONFIG_KEY = "builtin-registries";

    private static final String DEFAULT_CONTEXT = "/micrometer";
    private static final String SERVICE_NAME = "Micrometer";
    private static final String NO_MATCHING_REGISTRY_ERROR_MESSAGE = "No registered MeterRegistry matches the request";

    private static final Logger LOGGER = Logger.getLogger(MicrometerSupport.class.getName());

    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    private final CompositeMeterRegistry compositeMeterRegistry;
    private final String context;

    private final Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> enrolledRegistries;

    private MicrometerSupport(Builder builder) {
        context = builder.context;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(SERVICE_NAME, builder.crossOriginConfig);
        compositeMeterRegistry = new CompositeMeterRegistry();
        enrolledRegistries = builder.enrolledRegistries();
        enrolledRegistries.values().forEach(compositeMeterRegistry::add);
    }

    /**
     * Fluid builder for {@code MicrometerSupport}.
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code MicrometerSupport} using default settings.
     *
     * @return default MicrometerSupport
     */
    public static MicrometerSupport create() {
        return builder().build();
    }

    /**
     * Creates a new {@code MicrometerSupport} using the provided {@code Config} (anchored at the "metrics.micrometer" node).
     *
     * @param config Config settings for Micrometer set-up
     * @return newly-created MicrometerSupport
     */
    public static MicrometerSupport create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns the composite registry so apps can create and register meters on it.
     *
     * @return the composite registry
     */
    public MeterRegistry registry() {
        return compositeMeterRegistry;
    }

    @Override
    public void update(Routing.Rules rules) {
        configureEndpoint(rules);
    }

    // for testing
    Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> enrolledRegistries(){
        return enrolledRegistries;
    }

    private void configureEndpoint(Routing.Rules rules) {
        // CORS first
        rules
                .any(context, corsEnabledServiceHelper.processor())
                .any(new MetricsContextHandler(compositeMeterRegistry))
                .get(context, this::getOrOptions)
                .options(context, this::getOrOptions);

    }

    private void getOrOptions(ServerRequest serverRequest, ServerResponse serverResponse) {
        // Each meter registry is paired with a function. For each, invoke the function
        // looking for the first non-empty Optional<Handler> and invoke that handler. If
        // none matches then return an error response.
        enrolledRegistries.keySet().stream()
                .map(k -> k.apply(serverRequest))
                .findFirst()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .orElse((req, res) -> res
                        .status(Http.Status.NOT_ACCEPTABLE_406)
                        .send(NO_MATCHING_REGISTRY_ERROR_MESSAGE))
                .accept(serverRequest, serverResponse);
    }

    /**
     * Fluid builder for {@code MicrometerSupport} objects.
     */
    public static class Builder implements io.helidon.common.Builder<MicrometerSupport> {

        private CrossOriginConfig crossOriginConfig = null;
        private String context = DEFAULT_CONTEXT;
        private final Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> enrolledRegistries =
                new LinkedHashMap<>();

        private final Set<BuiltInRegistry> builtInRegistries = new HashSet<>();

        private final List<LogRecord> logRecords = new ArrayList<>();

        /**
         * Available built-in Micrometer meter registries.
         */
        public enum BuiltInRegistry {

            /**
             * Built-in Prometheus Micrometer registry.
             */
            PROMETHEUS(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) {

                private final Function<ServerRequest, Optional<Handler>> fn =
                        req -> req.headers().isAccepted(MediaType.TEXT_PLAIN)
                            ? Optional.of((rq, rs) -> rs.send(myRegistry().scrape()))
                            : Optional.empty();

                @Override
                Function<ServerRequest, Optional<Handler>> requestToHandlerFn() {
                    return fn;
                }

                private PrometheusMeterRegistry myRegistry() {
                    return PrometheusMeterRegistry.class.cast(registry());
                }
            },
            /**
             * Build-in JSON Micrometer registry.
             */
            // TODO
            JSON(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) {

                private final Function<ServerRequest, Optional<Handler>> fn =
                        req -> req.headers().isAccepted(MediaType.APPLICATION_JSON)
                            // TODO
                            ? Optional.of((rq, rs) -> rs.send(registry().toString()))
                            : Optional.empty();

                @Override
                Function<ServerRequest, Optional<Handler>> requestToHandlerFn() {
                    return fn;
                }
            };

            private final MeterRegistry registry;

            BuiltInRegistry(MeterRegistry registry) {
                this.registry = registry;
            }

            abstract Function<ServerRequest, Optional<Handler>> requestToHandlerFn();

            MeterRegistry registry() {
                return registry;
            }

            static BuiltInRegistry valueByName(String name) {
                return BuiltInRegistry.valueOf(name.trim().toUpperCase(Locale.ROOT));
            }
        }

        @Override
        public MicrometerSupport build() {
            return new MicrometerSupport(this);
        }

        /**
         * Override default configuration.
         *
         * @param config configuration instance
         * @return updated builder instance
         * @see MicrometerSupport for details about configuration keys
         */
        public Builder config(Config config) {
            // align with health checks
            DeprecatedConfig.get(config, "web-context", "context")
                    .asString()
                    .ifPresent(this::webContext);

            config.get(CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);

            config.get(BUILTIN_REGISTRIES_CONFIG_KEY)
                    .as(String.class)
                    .ifPresent(this::enrollBuiltInRegistries);

            return this;
        }

        /**
         * Add a built-in registry to those for which support is requested.
         *
         * @param builtInRegistry built-in registry to support
         * @return updated builder instance
         */
        public Builder enrollBuiltInRegistry(BuiltInRegistry builtInRegistry) {
            builtInRegistries.add(builtInRegistry);
            return this;
        }

        /**
         * Set a new root context for REST API of metrics.
         *
         * @param path context to use
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            if (path.startsWith("/")) {
                this.context = path;
            } else {
                this.context = "/" + path;
            }
            return this;
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         */
        public Builder crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
            return this;
        }

        /**
         * Records a {@code MetricRegistry} to be managed by {@code MicrometerSupport}, along with the function that returns an
         * {@code Optional} of a {@code Handler} for processing a given request to the Micrometer endpoint.
         *
         * @param meterRegistry registry to be enrolled
         * @param handlerFunction returns {@code Optional<Handler>}; if present, capable of responding to the specified request
         * @return updated builder instance
         */
        public Builder enrollRegistry(MeterRegistry meterRegistry, Function<ServerRequest, Optional<Handler>> handlerFunction) {
            enrolledRegistries.put(handlerFunction, meterRegistry);
            return this;
        }

        // For testing
        List<LogRecord> logRecords() {
            return logRecords;
        }

        private Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> enrolledRegistries() {
            Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> result = new LinkedHashMap<>(enrolledRegistries);
            builtInRegistries.forEach(builtInRegistry ->
                    result.put(builtInRegistry.requestToHandlerFn(), builtInRegistry.registry()));
            return result;
        }

        /**
         * Processes a comma-separated list of built-in Micrometer registry names, using the valid ones among them to
         * set the built-in registries in the builder.
         *
         * @param registries comma-separated list of built-in Micrometer registry names
         */
        private void enrollBuiltInRegistries(String registries) {
            List<BuiltInRegistry> result = new ArrayList<>();
            for (String registryName : registries.trim().split(",")) {
                try {
                    BuiltInRegistry builtInRegistry = BuiltInRegistry.valueByName(registryName);
                    result.add(builtInRegistry);
                } catch (IllegalArgumentException e) {
                    LogRecord logRecord = new LogRecord(Level.WARNING,
                            "Attempt to select unrecognized built-in Micrometer registry " + registryName + " ignored");
                    logRecords.add(logRecord);
                    LOGGER.log(logRecord);
                }
            }
            // Do not change previous settings if we did not find any valid new built-in registries selected.
            if (!result.isEmpty()) {
                builtInRegistries.clear();
                builtInRegistries.addAll(result);
                LOGGER.log(Level.FINE, () -> "Selecting built-in Micrometer registries " + result.toString());
            }
        }
    }

    // this class is created for cleaner tracing of web server handlers
    private static final class MetricsContextHandler implements Handler {

        private final MeterRegistry meterRegistry;

        private MetricsContextHandler(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            req.context().register(meterRegistry);
            req.next();
        }
    }
}
