/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Microprofile config wrapper of {@link Config}.
 */
public class MpConfig implements org.eclipse.microprofile.config.Config {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");
    private static final Map<Class<?>, Class<?>> TYPE_REPLACEMENTS = new HashMap<>();

    static {
        // all primitive types for which I use a built-in converter in MpConfigBuilder should be placed here
        TYPE_REPLACEMENTS.put(boolean.class, Boolean.class);
    }

    private final Supplier<Config> config;
    private final List<ConfigSource> mpConfigSources;
    private final Iterable<String> propertyNames;
    private final Set<Class> converterClasses;
    private final Map<Class<?>, Converter<?>> converters;

    /**
     * Create a new instance.
     *
     * @param config           configuration
     * @param mpConfigSources  config sources
     * @param converterClasses classes of converters
     * @param converters       class to converter mapping
     */
    public MpConfig(Config config,
                    List<ConfigSource> mpConfigSources,
                    Set<Class> converterClasses,
                    Map<Class<?>, Converter<?>> converters) {

        final AtomicReference<Config> ref = new AtomicReference<>(config);
        config.onChange(newConfig -> {
            ref.set(newConfig);
            return true;
        });

        this.config = ref::get;

        this.mpConfigSources = mpConfigSources;

        this.propertyNames =
                Stream.concat(mpConfigSources.stream()
                                      .flatMap(cs -> cs.getPropertyNames().stream()),
                              config.traverse(io.helidon.config.Config::isLeaf)
                                      .map(io.helidon.config.Config::key)
                                      .map(io.helidon.config.Config.Key::toString))
                        .collect(Collectors.toSet());

        this.converterClasses = new HashSet<>(converterClasses);
        this.converters = converters;
    }

    /**
     * Get a builder for config instances.
     *
     * @return a new builder
     */
    public static MpConfigBuilder builder() {
        return new MpConfigBuilder();
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = value.replace("\\,", ",");
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        try {
            if (propertyType.isArray()) {
                Class<?> element = propertyType.getComponentType();
                return (T) findArrayValue(propertyName, element);
            }

            return findValue(propertyName, propertyType);
        } catch (MissingValueException e) {
            throw new NoSuchElementException(e.getMessage());
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Return value as a {@link Set} of values.
     *
     * @param propertyName name of property
     * @param typeArg      type of elements in the set
     * @param <T>          type of elements
     * @return set with elements found in properties
     */
    public <T> Set<T> asSet(String propertyName, Class<T> typeArg) {
        return new HashSet<>(asList(propertyName, typeArg));
    }

    /**
     * Return value as a {@link List} of values.
     *
     * @param propertyName name of property
     * @param typeArg      type of elements in the list
     * @param <T>          type of elements
     * @return list with elements found in properties
     */
    public <T> List<T> asList(String propertyName, Class<T> typeArg) {
        if (typeArg == Config.class) {
            return config.get().get(propertyName).asList(typeArg);
        }
        return findInMpSources(propertyName)
                .map(value -> {
                    String[] valueArray = toArray(value);
                    List<T> result = new LinkedList<>();
                    for (String element : valueArray) {
                        result.add(convert(typeArg, element));
                    }
                    return result;
                })
                .orElseGet(() -> config.get().get(propertyName).asList(typeArg));
    }

    private <T> T findValue(String propertyName, Class<T> propertyType) {
        if (propertyType == Config.class) {
            return config.get().get(propertyName).as(propertyType);
        }
        //first iterate over mp sources, than use config
        return findInMpSources(propertyName)
                .map(value -> convert(propertyType, value))
                .orElseGet(() -> config.get().get(propertyName).as(propertyType));
    }

    private Object findArrayValue(String propertyName, Class<?> element) {
        // there should not be io.helidon.Config[]
        return findInMpSources(propertyName)
                .map(value -> asArray(value, element))
                .orElseGet(() -> {
                    Config arrayConfig = config.get().get(propertyName);
                    if (arrayConfig.isLeaf()) {
                        return asArray(arrayConfig.asString(), element);
                    }
                    List<?> objects = arrayConfig.asList(element);
                    Object array = Array.newInstance(element, objects.size());
                    for (int i = 0; i < objects.size(); i++) {
                        Array.set(array, i, objects.get(i));
                    }

                    return array;
                });
    }

    private Object asArray(String value, Class<?> element) {
        String[] valueArray = toArray(value);

        Object array = Array.newInstance(element, valueArray.length);
        for (int i = 0; i < valueArray.length; i++) {
            Array.set(array, i, convert(element, valueArray[i]));
        }

        return array;
    }

    private Optional<String> findInMpSources(String propertyName) {
        String propertyValue = null;
        for (ConfigSource source : mpConfigSources) {
            propertyValue = source.getValue(propertyName);
            if (null != propertyValue) {
                break;
            }
        }

        return Optional.ofNullable(propertyValue);
    }

    /**
     * Get value with a default if it does not exist.
     *
     * @param propertyName name of the property
     * @param propertyType type of the property
     * @param defaultValue default value correctly typed
     * @param <T>          type of the property
     * @return value from configuration or default value if not available
     */
    public <T> T getValue(String propertyName, Class<T> propertyType, T defaultValue) {
        return getOptionalValue(propertyName, propertyType).orElse(defaultValue);
    }

    /**
     * Get value with a default if it does not exist.
     *
     * @param propertyName name of the property
     * @param propertyType type of the property
     * @param defaultValue default value as String
     * @param <T>          type of the property
     * @return value from configuration or default value coerced to correct type if not available
     */
    public <T> T getValueWithDefault(String propertyName, Class<T> propertyType, String defaultValue) {
        return getOptionalValue(propertyName, propertyType).orElse(convert(propertyType, defaultValue));
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        try {
            return Optional.of(getValue(propertyName, propertyType));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Iterable<String> getPropertyNames() {
        return propertyNames;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return mpConfigSources;
    }

    /**
     * Check whether a converter exists for a specific class.
     *
     * @param clazz class to convert to
     * @return {@code true} if a converter exists for the specified class
     */
    public boolean hasConverter(Class<?> clazz) {
        return converterClasses.contains(clazz);
    }

    /**
     * Try to coerce the value to the specific type.
     *
     * @param type  type to return
     * @param value string value to parse
     * @param <T>   type we expect (e.g. String, Integer or a java bean)
     * @return null if value is null, transformed object otherwise
     * @throws IllegalArgumentException if the value cannot be converted to the specified type using.
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Class<T> type, String value) {
        if (null == value) {
            return null;
        }

        // now check if we have local added converter for this class
        Converter<?> maybeConverter = converters.get(mapType(type));

        if (null == maybeConverter) {
            // ask helidon config to do appropriate transformation (built-in, implicit and classpath mappers)
            Config c = Config.builder()
                    .disableSystemPropertiesSource()
                    .disableFilterServices()
                    .disableEnvironmentVariablesSource()
                    .sources(ConfigSources.from(CollectionsHelper.mapOf("key", value)))
                    .build();

            try {
                return c.get("key").as(type);
            } catch (ConfigMappingException e) {
                throw new IllegalArgumentException("Failed to convert " + value + " to " + type.getName(), e);
            }
        } else {
            return (T) maybeConverter.convert(value);
        }
    }

    private Class<?> mapType(Class<?> type) {
        return TYPE_REPLACEMENTS.getOrDefault(type, type);
    }

    /**
     * Get an instance of Helidon config (a tree structure) rather than the microprofile config.
     *
     * @return config instance that has the same properties as this instance
     */
    public Config getConfig() {
        // I need to create a config based on this config instance
        return Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .sources(ConfigSources.from(asMap()))
                .build();
    }

    /**
     * Get all properties of this config as a map.
     *
     * @return map where keys are configuration keys and values are associated string values
     */
    public Map<String, String> asMap() {
        // config from helidon config instance
        Map<String, String> map = new HashMap<>(config.get().asMap());
        // now add all properties from sources of MP config
        List<ConfigSource> configSources = new ArrayList<>(mpConfigSources);
        Collections.reverse(configSources);
        for (ConfigSource configSource : configSources) {
            map.putAll(configSource.getProperties());
        }
        return map;
    }

    @Override
    public String toString() {
        return "microprofileConfig: " + config.toString();
    }
}
