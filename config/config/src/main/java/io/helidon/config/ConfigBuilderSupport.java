/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.ConfiguredProvider;
import io.helidon.common.config.NamedService;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Methods used from generated code in builders when
 * {@link io.helidon.builder.api.Prototype.Configured} is used.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class ConfigBuilderSupport {
    // matches string between ${ } with a negative lookbehind if there is no backslash
    private static final String REGEX_REFERENCE = "(?<!\\\\)\\$\\{([^}:]+)(:.+?)?}";
    private static final Pattern PATTERN_REFERENCE = Pattern.compile(REGEX_REFERENCE);
    // matches a backslash with a positive lookahead if it is the backslash that encodes ${}
    private static final String REGEX_BACKSLASH = "\\\\(?=\\$\\{([^}]+)})";
    private static final Pattern PATTERN_BACKSLASH = Pattern.compile(REGEX_BACKSLASH);

    private ConfigBuilderSupport() {
    }

    /**
     * Used to discover services from {@link io.helidon.service.registry.ServiceRegistry} for builder options annotated
     * with {@link io.helidon.builder.api.Option.Provider}, if the blueprint is annotated with
     * {@link io.helidon.builder.api.Prototype.RegistrySupport}.
     *
     * @param config          configuration of the option
     * @param configKey       configuration key associated with this option
     * @param serviceRegistry service registry instance
     * @param providerType    type of the service provider (contract)
     * @param configType      type of the configuration
     * @param allFromRegistry whether to use all services from the registry
     * @param existingValues  existing values that was explicitly configured by the user
     * @param <S>             type of the service
     * @param <T>             type of the service provider (contract)
     * @return instances from the user augmented with instances from the registry
     */
    public static <S extends NamedService, T extends ConfiguredProvider<S>> List<S>
    discoverServices(Config config,
                     String configKey,
                     Optional<ServiceRegistry> serviceRegistry,
                     Class<T> providerType,
                     Class<S> configType,
                     boolean allFromRegistry,
                     List<S> existingValues)  {

        return ProvidedUtil.discoverServices(config,
                                             configKey,
                                             serviceRegistry,
                                             providerType,
                                             configType,
                                             allFromRegistry,
                                             existingValues);
    }

    /**
     * Used to discover service from {@link io.helidon.service.registry.ServiceRegistry} for builder options annotated
     * with {@link io.helidon.builder.api.Option.Provider}, if the blueprint is annotated with
     * {@link io.helidon.builder.api.Prototype.RegistrySupport}.
     *
     * @param config           configuration of the option
     * @param configKey        configuration key associated with this option
     * @param serviceRegistry  service registry instance
     * @param providerType     type of the service provider (contract)
     * @param configType       type of the configuration
     * @param discoverServices whether to discover services from registry
     * @param existingValue    existing value that was explicitly configured by the user
     * @param <T>              type of the service
     * @return an instance, if available in the registry, or if provided by the user (user's value wins)
     */
    public static <T extends NamedService> Optional<T>
    discoverService(Config config,
                    String configKey,
                    Optional<ServiceRegistry> serviceRegistry,
                    Class<? extends ConfiguredProvider<T>> providerType,
                    Class<T> configType,
                    boolean discoverServices,
                    Optional<T> existingValue) {

        return ProvidedUtil.discoverService(config,
                                            configKey,
                                            serviceRegistry,
                                            providerType,
                                            configType,
                                            discoverServices,
                                            existingValue);
    }


    /**
     * Discover services from configuration.
     * If already configured instances contain a service of the same type and name that would be added from
     * configuration, the configuration would be ignored (e.g. the user must make a choice whether to configure, or
     * set using an API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service)
     * @param providerType         type of the service provider interface, used to lookup from {@link java.util.ServiceLoader}
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingInstances    already configured instances
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return list of discovered services, ordered by {@link io.helidon.common.Weight} (highest weight is first in the list)
     */
    public static <S extends NamedService, T extends ConfiguredProvider<S>> List<S>
    discoverServices(Config config,
                     String configKey,
                     Class<T> providerType,
                     Class<S> configType,
                     boolean allFromServiceLoader,
                     List<S> existingInstances) {
        return ProvidedUtil.discoverServices(config,
                                             configKey,
                                             HelidonServiceLoader.create(providerType),
                                             providerType,
                                             configType,
                                             allFromServiceLoader,
                                             existingInstances);
    }

    /**
     * Discover service from configuration. If an instance is already configured using a builder, it will not be
     * discovered from configuration (e.g. the user must make a choice whether to configure, or set using API).
     *
     * @param config               configuration located at the parent node of the service providers
     * @param configKey            configuration key of the provider list
     *                             (either a list node, or object, where each child is one service - this method requires
     *                             *                             zero to one configured services)
     * @param providerType         type of the service provider interface, used to lookup from {@link java.util.ServiceLoader}
     * @param configType           type of the configured service
     * @param allFromServiceLoader whether all services from service loader should be used, or only the ones with configured
     *                             node
     * @param existingValue        value already configured, if the name is same as discovered from configuration
     * @param <S>                  type of the expected service
     * @param <T>                  type of the configured service provider that creates instances of S
     * @return the first service (ordered by {@link io.helidon.common.Weight} that is discovered, or empty optional if none
     *         is found
     */
    public static <S extends NamedService, T extends ConfiguredProvider<S>> Optional<S>
    discoverService(Config config,
                    String configKey,
                    Class<T> providerType,
                    Class<S> configType,
                    boolean allFromServiceLoader,
                    Optional<S> existingValue) {
        return ProvidedUtil.discoverService(config,
                                            configKey,
                                            HelidonServiceLoader.create(providerType),
                                            providerType,
                                            configType,
                                            allFromServiceLoader,
                                            existingValue);
    }

    /**
     * Extension of {@link io.helidon.builder.api.Prototype.Builder} that supports configuration.
     * If a blueprint is marked as {@code @Configured}, build will accept configuration.
     *
     * @param <BUILDER>   type of the builder
     * @param <PROTOTYPE> type of the prototype to be built
     */
    public interface ConfiguredBuilder<BUILDER, PROTOTYPE> extends Prototype.Builder<BUILDER, PROTOTYPE>,
            io.helidon.common.config.ConfigBuilderSupport.ConfiguredBuilder<BUILDER, PROTOTYPE> {
        /**
         * Update builder from configuration.
         * Any configured option that is defined on this prototype will be checked in configuration, and if it exists,
         * it will override current value for that option on this builder.
         * Options that do not exist in the provided config will not impact current values.
         * The config instance is kept and may be used in builder decorator, it is not available in prototype implementation.
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        BUILDER config(Config config);
    }

    /**
     * Resolves an expression that may contain references to configuration values with possible default values.
     * Nested expression are not allowed.
     *
     * @param config configuration instance
     * @param expression expression to resolve, such as
     *                   <pre>${service.scheme:http}://${service.host:localhost}:${service.port}</pre>, where
     *                   {@code service.scheme} has a default value of {@code http},
     *                   {@code service.host} has a default value of {@code localhost},
     *                   and {@code service.port} does not have a default value, and will fail if not configured
     * @return expression value with values retrieved from the {@code config} instance
     */
    public static String resolveExpression(io.helidon.config.Config config, String expression) {
        Matcher m = PATTERN_REFERENCE.matcher(expression);

        try {
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String configKey = m.group(1);
                String defaultValue = m.group(2);

                if (defaultValue == null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(config.get(configKey).asString().get()));
                } else {
                    // remove the :
                    defaultValue = defaultValue.substring(1);
                    m.appendReplacement(sb, Matcher.quoteReplacement(config.get(configKey).asString().orElse(defaultValue)));
                }
            }

            m.appendTail(sb);
            m = PATTERN_BACKSLASH.matcher(sb.toString());
            return m.replaceAll("");
        } catch (ConfigException e) {
            throw new ConfigException("Failed to resolve expression: " + expression, e);
        }
    }

    /**
     * Resolve expressions from a set of values.
     * This is mostly used from annotations (generated code), where the {@code expressions} is either a single string,
     * that resolved to a configured value (with comma separated defaults), or multiple strings, each representing a value.
     *
     * @param config configuration to obtain values for expression
     * @param expressions expressions to use to get actual values
     * @return a set of values to be used by an application
     */
    public static Set<String> resolveSetExpressions(Config config, Collection<String> expressions) {
        if (expressions.size() == 1) {
            String expression = expressions.iterator().next();
            if (expression.contains("${") && expression.contains("}")) {
                // the config value may be an array itself (should be)
                return resolveExpressions(config, expression);
            }
            return Set.of(expression);
        }
        return expressions.stream()
                .map(it -> ConfigBuilderSupport.resolveExpression(config, it))
                .collect(Collectors.toSet());
    }

    private static Set<String> resolveExpressions(Config config, String expression) {
        // single expression, may be something as "${my.values:first,second}" - i.e. an array in config, an array default values

        if (!expression.startsWith("${") || !expression.endsWith("}")) {
            throw new IllegalArgumentException("Invalid expression for a set of values: \"" + expression + "\", "
                                                       + "expression must be the whole string, i.e."
                                                       + " \"${key:comma-separated-defaults}\".");
        }

        Matcher m = PATTERN_REFERENCE.matcher(expression);

        try {
            if (m.matches()) {
                String configKey = m.group(1);
                String defaultValue = m.group(2);

                ConfigValue<List<String>> configValues = config.get(configKey).asList(String.class);
                if (defaultValue == null || configValues.isPresent())  {
                    return Set.copyOf(configValues.orElseGet(List::of));
                } else {
                    return Stream.of(defaultValue.split(","))
                            .map(String::trim)
                            .collect(Collectors.toSet());
                }
            }

            return Set.of(expression);
        } catch (ConfigException e) {
            throw new ConfigException("Failed to resolve expression: " + expression, e);
        }
    }
}
