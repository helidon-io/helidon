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

package io.helidon.integrations.micrometer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

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
 *     {@code ServerRequest} and returns an {@code Optional<HAND>}. The function is expected to inspect the request,
 *     and if
 *     it wants to process that request (which means, if <em>that</em> registry should be used to respond to that request),
 *     it returns an {@code Optional.of} a HAND. The handler uses the registry-specific mechanism for retrieving
 *     formatted meter and metrics information and sets and returns the HTTP response. If the function decides that its
 *     corresponding meter registry <em>is not</em> suitable for that particular request, it returns an {@code Optional.empty()}.
 * </p>
 * <p>
 *     When a Micrometer request arrives, the Helidon code invokes the functions associated with all enrolled registries, in the
 *     order of enrollment. The first function that returns a non-empty {@code Optional} wins and must populate and return the
 *     RESP.
 * </p>
 *
 * @param <REQ> The server request
 * @param <RESP> The server response
 * @param <HAND> The server handler
 */
public abstract class MeterRegistryFactory<REQ, RESP, HAND> {

    /**
     * Config key for specifying built-in registry types to enroll.
     */
    static final String BUILTIN_REGISTRIES_CONFIG_KEY = "builtin-registries";

    public static final String NO_MATCHING_REGISTRY_ERROR_MESSAGE = "No registered MeterRegistry matches the request";
    private static final Logger LOGGER = Logger.getLogger(MeterRegistryFactory.class.getName());

    protected final CompositeMeterRegistry compositeMeterRegistry;
    protected final List<Enrollment<REQ, HAND>> registryEnrollments;

    // for testing
    protected final Map<BuiltInRegistryType, MeterRegistry> builtInRegistryEnrollments = new HashMap<>();

    protected MeterRegistryFactory(Builder<REQ, RESP, HAND> builder) {
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

    public HAND matchingHandler(REQ req, RESP res) {
        return registryEnrollments.stream()
                .map(e -> e.handlerFn().apply(req))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(notMatch(req, res));
    }

    protected abstract HAND notMatch(REQ request, RESP response);

    /**
     * Builder for constructing {@code MeterRegistryFactory} instances.
     * @param <REQ> The server request
     * @param <RESP> The server response
     * @param <HAND> The server handler
     */
    public static abstract class Builder<REQ, RESP, HAND> implements
        io.helidon.common.Builder<Builder<REQ, RESP, HAND>, MeterRegistryFactory<REQ, RESP, HAND>> {

        private final List<Enrollment<REQ, HAND>> explicitRegistryEnrollments = new ArrayList<>();

        private final Map<BuiltInRegistryType, MicrometerPrometheusRegistrySupport<REQ, HAND>>
            builtInRegistriesRequested = new HashMap<>();

        protected final List<LogRecord> logRecords = new ArrayList<>();

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
        public Builder<REQ, RESP, HAND> config(Config config) {

            config.get(BUILTIN_REGISTRIES_CONFIG_KEY)
                    .ifExists(this::enrollBuiltInRegistries);

            return this;
        }

        protected abstract MicrometerPrometheusRegistrySupport<REQ, HAND> createPrometheus(BuiltInRegistryType builtInRegistryType,
                Optional<MeterRegistryConfig> meterRegistryConfig);
        
        protected abstract MicrometerPrometheusRegistrySupport<REQ, HAND> create(BuiltInRegistryType type,
                ConfigValue<Config> node);
        
        /**
         * Enrolls a built-in registry type to support.
         *
         * @param builtInRegistryType built-in meter registry type to support
         * @param meterRegistryConfig appropriate {@code MeterRegistryConfig} instance setting up the meter registry
         * @return updated builder instance
         */
        public Builder<REQ, RESP, HAND> enrollBuiltInRegistry(BuiltInRegistryType builtInRegistryType,
                MeterRegistryConfig meterRegistryConfig) {
            builtInRegistriesRequested.put(builtInRegistryType, createPrometheus(builtInRegistryType, Optional.of(meterRegistryConfig)));
            return this;
        }

        /**
         * Enrolls a built-in registry type using the default configuration for that type.
         *
         * @param builtInRegistryType  built-in meter registry type to support
         * @return updated builder instance
         */
        public Builder<REQ, RESP, HAND> enrollBuiltInRegistry(BuiltInRegistryType builtInRegistryType) {
            builtInRegistriesRequested.put(builtInRegistryType, createPrometheus(builtInRegistryType, Optional.empty()));
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
        public Builder<REQ, RESP, HAND> enrollRegistry(MeterRegistry meterRegistry, Function<REQ, Optional<HAND>> handlerFunction) {
            explicitRegistryEnrollments.add(new Enrollment<REQ, HAND>(meterRegistry, handlerFunction));
            return this;
        }

        private List<Enrollment<REQ, HAND>> explicitAndBuiltInEnrollments() {
            List<Enrollment<REQ, HAND>> result = new ArrayList<>(explicitRegistryEnrollments);
            builtInRegistriesRequested.forEach((builtInRegistrySupportType, builtInRegistrySupport) -> {
                MeterRegistry meterRegistry = builtInRegistrySupport.registry();
                result.add(new Enrollment<REQ, HAND>(meterRegistry,
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
            // in case config is created from MP config, node type is OBJECT instead of LIST
            // as MP config flattens everything
            Map<BuiltInRegistryType, MicrometerPrometheusRegistrySupport<REQ, HAND>> candidateBuiltInRegistryTypes =
                    new HashMap<>();
            List<String> unrecognizedTypes = new ArrayList<>();

            registriesConfig.asList(Config.class)
                    .ifPresent(confList -> {
                        for (Config registryConfig : confList) {
                            String registryType = registryConfig.get("type").asString().get();
                            try {
                                BuiltInRegistryType type =
                                        BuiltInRegistryType.valueByName(registryType);

                                MicrometerPrometheusRegistrySupport<REQ, HAND> builtInRegistrySupport =
                                        create(type, registryConfig.asNode());

                                candidateBuiltInRegistryTypes.put(type, builtInRegistrySupport);
                            } catch (BuiltInRegistryType.UnrecognizedBuiltInRegistryTypeException e) {
                                unrecognizedTypes.add(e.unrecognizedType());
                                logRecords.add(new LogRecord(Level.WARNING,
                                                             String.format(
                                                                     "Ignoring unrecognized Micrometer built-in registry type %s",
                                                                     e.unrecognizedType())));
                            }
                        }
                    });

            if (!unrecognizedTypes.isEmpty()) {
                LOGGER.log(Level.WARNING, String.format("Ignoring unrecognized Micrometer built-in registries: %s",
                                                        unrecognizedTypes));
            }

            // Do not change previous settings if we did not find any valid new built-in registries selected.
            if (!candidateBuiltInRegistryTypes.isEmpty()) {
                builtInRegistriesRequested.clear();
                builtInRegistriesRequested.putAll(candidateBuiltInRegistryTypes);
                LOGGER.log(Level.FINE,
                        () -> "Selecting built-in Micrometer registries " + candidateBuiltInRegistryTypes);
            }
        }
    }

    private static class Enrollment<REQ, HAND> {

        private final MeterRegistry meterRegistry;
        private final Function<REQ, Optional<HAND>> handlerFn;

        private Enrollment(MeterRegistry meterRegistry, Function<REQ, Optional<HAND>> handlerFn) {
            this.meterRegistry = meterRegistry;
            this.handlerFn = handlerFn;
        }

        private MeterRegistry meterRegistry() {
            return meterRegistry;
        }

        private Function<REQ, Optional<HAND>> handlerFn() {
            return handlerFn;
        }
    }

}
