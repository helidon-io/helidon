/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;

/**
 * Provides access to the {@link MeterRegistry} Helidon SE uses to collect meters and report their metrics.
 * <p>
 *     In reality, we use a {@link CompositeMeterRegistry} to which we, by default,
 *     add a {@link io.micrometer.prometheus.PrometheusMeterRegistry} and to which the developer can add,
 *     via either configuration or builder, other {@code MeterRegistry} instances.
 * </p>
 * <h2>Using the factory</h2>
 * <p>
 *     Use this factory in either of two ways:
 *     <ol>
 *         <li>
 *             Access the singleton instance via {@link #getInstance()} or {@link #getInstance(Builder)}. The factory remembers
 *             the factory instance, lazily instantiated by the most recent invocation of either method.
 *         </li>
 *         <li>
 *             A custom instance via {@link #create()} or {@link #create(Config)}. Instances of the factory created this way
 *             are independent of the singleton created by {@code getInstance()} or {@code getInstance(Builder)}.
 *         </li>
 *     </ol>
 *
 * <h2>Adding developer-requested registries</h2>
 * <p>
 *     In Micrometer, different registries report their contents in different formats. Further, there is no common abstract
 *     method defined on {@code MeterRegistry} which all implementations override; each {@code MeterRegistry}  has its own
 *     particular way of furnishing metrics output.
 *
 *     By default, we use a {@code PrometheusMeterRegistry} to support Prometheus/OpenMetrics format. Developers can enroll other
 *     registries to support other formats. We need to know which registry to use in response to receiving an HTTP request for
 *     Micrometer metrics output.
 * </p>
 * <p>
 *     To allow us to do this, when our code or developer's enrolls a registry, it also passes a function that accepts a
 *     {@code ServerRequest} and returns an {@code Optional<Handler>}. The function is expected to inspect the request,
 *     and if
 *     it wants to process that request (which means, if <em>that</em> registry should be used to respond to that request),
 *     it returns an {@code Optional.of} a {@link Handler}. The handler uses the registry-specific mechanism for retrieving
 *     formatted meter and metrics information and sets and returns the HTTP response. If the function decides that its
 *     corresponding meter registry <em>is not</em> suitable for that particular request, it returns an {@code Optional.empty()}.
 * </p>
 * <p>
 *     When a Micrometer request arrives, the Helidon code invokes the functions associated with all enrolled registries, in the
 *     order of enrollment. The first function that returns a non-empty {@code Optional} wins and must populate and return the
 *     {@link ServerResponse}.
 * </p>
 */
public final class MeterRegistryFactory {

    /**
     * Config key for specifying built-in registry types to enroll.
     */
    static final String BUILTIN_REGISTRIES_CONFIG_KEY = "builtin-registries";

    private static final String NO_MATCHING_REGISTRY_ERROR_MESSAGE = "No registered MeterRegistry matches the request";
    private static final Logger LOGGER = Logger.getLogger(MeterRegistryFactory.class.getName());

    private static MeterRegistryFactory instance = create();

    private final CompositeMeterRegistry compositeMeterRegistry;
    private final List<Enrollment> registryEnrollments;

    // for testing
    private final Map<BuiltInRegistryType, MeterRegistry> builtInRegistryEnrollments = new HashMap<>();

    /**
     * Creates a new factory using default settings (no config).
     *
     * @return initialized MeterRegistryFactory
     */
    public static MeterRegistryFactory create() {
        return create(Config.empty());
    }

    /**
     * Creates a new factory using the specified config.
     *
     * @param config the config to use in initializing the factory
     * @return initialized MeterRegistryFactory
     */
    public static MeterRegistryFactory create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns the singleton instance of the factory.
     *
     * @return factory singleton
     */
    public static MeterRegistryFactory getInstance() {
        return instance;
    }

    /**
     * Creates and saves as the singleton a new factory.
     *
     * @param builder the Builder to use in constructing the new singleton instance
     *
     * @return MeterRegistryFactory using the Builder
     */
    public static MeterRegistryFactory getInstance(Builder builder) {
        instance = builder.build();
        return instance;
    }

    /**
     * Returns a new {@code Builder} for constructing a {@code MeterRegistryFactory}.
     *
     * @return initialized builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private MeterRegistryFactory(Builder builder) {
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (builder.explicitAndBuiltInEnrollments().isEmpty()) {
            builder.enrollBuiltInRegistry(BuiltInRegistryType.PROMETHEUS);
        }
        registryEnrollments = builder.explicitAndBuiltInEnrollments();
        builder.builtInRegistriesRequested.forEach((builtInRegistryType, builtInRegistrySupport) -> {
            MeterRegistry meterRegistry = builtInRegistrySupport.registry();
            builtInRegistryEnrollments.put(builtInRegistryType, meterRegistry);
        });
        registryEnrollments.forEach(e -> compositeMeterRegistry.add(e.meterRegistry()));

    }

    /**
     * Returns the {@code MeterRegistry} associated with this factory instance.
     *
     * @return the meter registry
     */
    public MeterRegistry meterRegistry() {
        return compositeMeterRegistry;
    }

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

            private static final long serialVersionUID = 9079876961827144352L;
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

    Handler matchingHandler(ServerRequest serverRequest, ServerResponse serverResponse) {
        return registryEnrollments.stream()
                .map(e -> e.handlerFn().apply(serverRequest))
                .findFirst()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .orElse((req, res) -> res
                        .status(Http.Status.NOT_ACCEPTABLE_406)
                        .send(NO_MATCHING_REGISTRY_ERROR_MESSAGE));
    }

    /**
     * Builder for constructing {@code MeterRegistryFactory} instances.
     */
    public static class Builder implements io.helidon.common.Builder<MeterRegistryFactory> {

        private final List<Enrollment> explicitRegistryEnrollments = new ArrayList<>();

        private final Map<BuiltInRegistryType, MicrometerBuiltInRegistrySupport> builtInRegistriesRequested = new HashMap<>();

        private final List<LogRecord> logRecords = new ArrayList<>();

        @Override
        public MeterRegistryFactory build() {
            return new MeterRegistryFactory(this);
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

            config.get(BUILTIN_REGISTRIES_CONFIG_KEY)
                    .ifExists(this::enrollBuiltInRegistries);

            return this;
        }

        /**
         * Enrolls a built-in registry type to support.
         *
         * @param builtInRegistryType built-in meter registry type to support
         * @param meterRegistryConfig appropriate {@code MeterRegistryConfig} instance setting up the meter registry
         * @return updated builder instance
         */
        public Builder enrollBuiltInRegistry(BuiltInRegistryType builtInRegistryType, MeterRegistryConfig meterRegistryConfig) {
            MicrometerBuiltInRegistrySupport builtInRegistrySupport = MicrometerBuiltInRegistrySupport.create(builtInRegistryType,
                    meterRegistryConfig);
            builtInRegistriesRequested.put(builtInRegistryType, builtInRegistrySupport);
            return this;
        }

        /**
         * Enrolls a built-in registry type using the default configuration for that type.
         *
         * @param builtInRegistryType  built-in meter registry type to support
         * @return updated builder instance
         */
        public Builder enrollBuiltInRegistry(BuiltInRegistryType builtInRegistryType) {
            MicrometerBuiltInRegistrySupport builtInRegistrySupport =
                    MicrometerBuiltInRegistrySupport.create(builtInRegistryType);
            builtInRegistriesRequested.put(builtInRegistryType, builtInRegistrySupport);
            return this;
        }

        /**
         * Records a {@code MetricRegistry} to be managed by {@code MicrometerSupport}, along with the function that returns an
         * {@code Optional} of a {@code Handler} for processing a given request to the Micrometer endpoint.
         *
         * @param meterRegistry the registry to enroll
         * @param handlerFunction returns {@code Optional<Handler>}; if present, capable of responding to the specified request
         * @return updated builder instance
         */
        public Builder enrollRegistry(MeterRegistry meterRegistry, Function<ServerRequest, Optional<Handler>> handlerFunction) {
            explicitRegistryEnrollments.add(new Enrollment(meterRegistry, handlerFunction));
            return this;
        }

        // For testing
        List<LogRecord> logRecords() {
            return logRecords;
        }

        private List<Enrollment> explicitAndBuiltInEnrollments() {
            List<Enrollment> result = new ArrayList<>(explicitRegistryEnrollments);
            builtInRegistriesRequested.forEach((builtInRegistrySupportType, builtInRegistrySupport) -> {
                MeterRegistry meterRegistry = builtInRegistrySupport.registry();
                result.add(new Enrollment(meterRegistry,
                        builtInRegistrySupport.requestToHandlerFn(meterRegistry)));
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

            Map<BuiltInRegistryType, MicrometerBuiltInRegistrySupport> candidateBuiltInRegistryTypes =
                    new HashMap<>();
            List<String> unrecognizedTypes = new ArrayList<>();

            for (Config registryConfig : registriesConfig.asNodeList().get()) {
                String registryType = registryConfig.get("type").asString().get();
                try {
                    BuiltInRegistryType type =
                            BuiltInRegistryType.valueByName(registryType);

                    MicrometerBuiltInRegistrySupport builtInRegistrySupport =
                            MicrometerBuiltInRegistrySupport.create(type, registryConfig.asNode());
                    if (builtInRegistrySupport != null) {
                        candidateBuiltInRegistryTypes.put(type, builtInRegistrySupport);
                    }
                } catch (BuiltInRegistryType.UnrecognizedBuiltInRegistryTypeException e) {
                    unrecognizedTypes.add(e.unrecognizedType());
                    logRecords.add(new LogRecord(Level.WARNING,
                            String.format("Ignoring unrecognized Micrometer built-in registry type %s", e.unrecognizedType())));
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

    // for testing
    Set<MeterRegistry> registries() {
        return compositeMeterRegistry.getRegistries();
    }

    // for testing
    Map<BuiltInRegistryType, MeterRegistry> enrolledBuiltInRegistries() {
        return builtInRegistryEnrollments;
    }


    private static class Enrollment {

        private final MeterRegistry meterRegistry;
        private final Function<ServerRequest, Optional<Handler>> handlerFn;

        private Enrollment(MeterRegistry meterRegistry, Function<ServerRequest, Optional<Handler>> handlerFn) {
            this.meterRegistry = meterRegistry;
            this.handlerFn = handlerFn;
        }

        private MeterRegistry meterRegistry() {
            return meterRegistry;
        }

        private Function<ServerRequest, Optional<Handler>> handlerFn() {
            return handlerFn;
        }
    }
}
