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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
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
import io.micrometer.core.instrument.config.MeterRegistryConfig;

import static io.helidon.webserver.cors.CorsEnabledServiceHelper.CORS_CONFIG_KEY;

/**
 * Implements simple Micrometer support.
 * <p>
 * Developers create Micrometer {@code MeterRegistry} objects and enroll them with
 * {@link Builder}, providing with each enrollment a Helidon {@code Handler} for expressing the registry's
 * data in an HTTP response.
 * </p>
 * <p>Alternatively, developers can enroll any of the built-in registries represented by
 * the {@link BuiltInRegistryType} enum.</p>
 * <p>
 * Having enrolled Micrometer meter registries with {@code MicrometerSupport.Builder} and built the
 * {@code MicrometerSupport} object, developers can invoke the {@link #registry()} method and use the returned {@code
 * MeterRegistry} to create or locate meters.
 * </p>
 */
public class MicrometerSupport implements Service {

    /**
     * Config key for specifying built-in registry types to enroll.
     */
    public static final String BUILTIN_REGISTRIES_CONFIG_KEY = "builtin-registries";

    /**
     * Available built-in registry types.
     */
    public enum BuiltInRegistryType {

        /**
         * Prometheus built-in registry type.
         */
        PROMETHEUS;

        /**
         * Describes an unrecognized built-in registry type.
         */
        public static class UnrecognizedBuiltInRegistryTypeException extends Exception {

            private final String unrecognizedType;

            /**
             * Creates a new instance of the exception.
             *
             * @param unrecognizedType the unrecognized type
             */
            public UnrecognizedBuiltInRegistryTypeException(String unrecognizedType) {
                super();
                this.unrecognizedType = unrecognizedType;
            }

            /**
             * Returns the unrecognized type.
             *
             * @return the unrecognized type
             */
            public String unrecognizedType() {
                return unrecognizedType;
            }

            @Override
            public String getMessage() {
                return "Unrecognized built-in Micrometer registry type: " + unrecognizedType;
            }
        }

        static BuiltInRegistryType valueByName(String name) throws UnrecognizedBuiltInRegistryTypeException {
            try {
                return valueOf(name.trim()
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new UnrecognizedBuiltInRegistryTypeException(name);
            }
        }
    }

    private static final String DEFAULT_CONTEXT = "/micrometer";
    private static final String SERVICE_NAME = "Micrometer";
    private static final String NO_MATCHING_REGISTRY_ERROR_MESSAGE = "No registered MeterRegistry matches the request";

    private static final Logger LOGGER = Logger.getLogger(MicrometerSupport.class.getName());

    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    private final CompositeMeterRegistry compositeMeterRegistry;
    private final String context;

    private final Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> enrolledRegistries;

    // for testing
    private final Map<BuiltInRegistryType, MeterRegistry> enrolledBuiltInRegistries = new HashMap<>();

    private MicrometerSupport(Builder builder) {
        context = builder.context;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(SERVICE_NAME, builder.crossOriginConfig);
        compositeMeterRegistry = new CompositeMeterRegistry();
        enrolledRegistries = builder.registriesToEnroll();
        builder.builtInRegistriesRequested.forEach((builtInRegistryType, builtInRegistrySupport) -> {
            MeterRegistry meterRegistry = builtInRegistrySupport.registry();
            enrolledBuiltInRegistries.put(builtInRegistryType, meterRegistry);
        });
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

    // for testing
    Map<BuiltInRegistryType, MeterRegistry> enrolledBuiltInRegistries() {
        return enrolledBuiltInRegistries;
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
        /*
          Each meter registry is paired with a function. For each, invoke the function
          looking for the first non-empty Optional<Handler> and invoke that handler. If
          none matches then return an error response.
         */
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
        private final Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> explicitlyEnrolledRegistries =
                new LinkedHashMap<>();

        private final Map<BuiltInRegistryType, BuiltInRegistrySupport> builtInRegistriesRequested = new HashMap<>();

        private final List<LogRecord> logRecords = new ArrayList<>();

        /**
         * Available built-in Micrometer meter registry types.
         */

        @Override
        public MicrometerSupport build() {
            return new MicrometerSupport(this);
        }

        /**
         * Override default configuration.
         * <p>
         * The config items supported vary from one built-in type to the next. See the documentation for the
         * corresponding {@code MicrometerRegistryConfig} for details.
         * </p>
         *
         * @param config configuration instance
         * @return updated builder instance
         */
        public Builder config(Config config) {
            DeprecatedConfig.get(config, "web-context", "context")
                    .asString()
                    .ifPresent(this::webContext);

            config.get(CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);

            config.get(BUILTIN_REGISTRIES_CONFIG_KEY)
                    .ifExists(this::enrollBuiltInRegistries);

            return this;
        }

        /**
         * Add a built-in registry instance to support during this execution.
         *
         * @param builtInRegistryType built-in meter registry type to support
         * @param meterRegistryConfig appropriate {@code MeterRegistryConfig} instance setting up the meter registry
         * @return updated builder instance
         */
        public Builder enrollBuiltInRegistry(BuiltInRegistryType builtInRegistryType, MeterRegistryConfig meterRegistryConfig) {
            BuiltInRegistrySupport builtInRegistrySupport = BuiltInRegistrySupport.create(builtInRegistryType,
                    meterRegistryConfig);
            builtInRegistriesRequested.put(builtInRegistryType, builtInRegistrySupport);
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
            explicitlyEnrolledRegistries.put(handlerFunction, meterRegistry);
            return this;
        }

        // For testing
        List<LogRecord> logRecords() {
            return logRecords;
        }

        private Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> registriesToEnroll() {
            /*
             * Combine the explicitly enrolled registries with the selected built-in ones.
             */
            Map<Function<ServerRequest, Optional<Handler>>, MeterRegistry> result =
                    new LinkedHashMap<>(explicitlyEnrolledRegistries);
            builtInRegistriesRequested.forEach((builtInRegistrySupportType, builtInRegistrySupport) -> {
                        MeterRegistry meterRegistry = builtInRegistrySupport.registry();
                        result.put(builtInRegistrySupport.requestToHandlerFn(meterRegistry), meterRegistry);
                    });
            return result;
        }

        /**
         * Enrolls built-in registries specified in a {@code Config} object which is expected to be a @{code LIST} with each
         * element an {@code OBJECT} with at least a {@code type} item.
         * <p>
         * Any additional config items can vary from one built-in registry type to the next.
         * </p>
         * <p>
         * If the config specifies one or more unrecognized {@code type}s, the builder ignores them, logs a {@code WARNING}
         * message reporting them, and continues.
         * </p>
         *
         * @param registriesConfig {@code Config} object for the 1 or more {@code builtin-registries} entries
         */
        private void enrollBuiltInRegistries(Config registriesConfig) {

            if (registriesConfig.type() != Config.Type.LIST) {
                throw new IllegalArgumentException("Expected Micrometer config " + BUILTIN_REGISTRIES_CONFIG_KEY + " as a LIST "
                        + "but found " + registriesConfig.type().name());
            }

            Map<BuiltInRegistryType, BuiltInRegistrySupport> candidateBuiltInRegistryTypes = new HashMap<>();
            List<String> unrecognizedTypes = new ArrayList<>();

            for (Config registryConfig : registriesConfig.asNodeList().get()) {
                String registryType = registryConfig.get("type").asString().get();
                try {
                    BuiltInRegistryType type = BuiltInRegistryType.valueByName(registryType);

                    BuiltInRegistrySupport builtInRegistrySupport = BuiltInRegistrySupport.create(type, registryConfig.asNode());
                    if (builtInRegistrySupport != null) {
                        candidateBuiltInRegistryTypes.put(type, builtInRegistrySupport);
                    }
                } catch (BuiltInRegistryType.UnrecognizedBuiltInRegistryTypeException e) {
                    unrecognizedTypes.add(e.unrecognizedType());
                    logRecords.add(new LogRecord(Level.WARNING,
                            String.format("Ignoring unrecognized built-in registry type %s", e.unrecognizedType())));
                }
            }

            if (!unrecognizedTypes.isEmpty()) {
                LOGGER.log(Level.WARNING, String.format("Ignoring unrecognized Micrometer built-in registries: %s",
                        unrecognizedTypes.toString()));
            }

            // Do not change previous settings if we did not find any valid new built-in registries selected.
            if (!candidateBuiltInRegistryTypes.isEmpty()) {
                builtInRegistriesRequested.clear();
                builtInRegistriesRequested.putAll(candidateBuiltInRegistryTypes);
                LOGGER.log(Level.FINE,
                        () -> "Selecting built-in Micrometer registries " + candidateBuiltInRegistryTypes.toString());
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
