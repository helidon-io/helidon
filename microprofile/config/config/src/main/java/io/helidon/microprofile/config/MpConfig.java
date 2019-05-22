/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Microprofile config wrapper of {@link Config}.
 */
public final class MpConfig implements org.eclipse.microprofile.config.Config {
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");

    private final Supplier<Config> config;
    private final List<ConfigSource> mpConfigSources;
    private final Iterable<String> propertyNames;
    private final Map<Class<?>, Converter<?>> converters;
    private final AtomicReference<Config> helidonConverter;

    /**
     * Create a new instance.
     *  @param config           configuration
     * @param mpConfigSources  config sources
     * @param converters       class to converter mapping
     */
    MpConfig(Config config,
             List<ConfigSource> mpConfigSources,
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
                              config.traverse(Config::isLeaf)
                                      .map(Config::key)
                                      .map(Config.Key::toString))
                        .collect(Collectors.toSet());

        this.converters = converters;
        this.helidonConverter = new AtomicReference<>();
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
            return config.get().get(propertyName).asList(typeArg).get();
        }
        return findInMpSources(propertyName)
                .map(value -> toList(value, typeArg))
                .orElseGet(() -> config.get().get(propertyName).asList(typeArg).get());
    }

    private <T> List<T> toList(final String value, final Class<T> elementType) {
        final String[] valueArray = toArray(value);
        final List<T> result = new ArrayList<>();
        for (final String element : valueArray) {
            result.add(convert(elementType, element));
        }
        return result;
    }

    <T> T findValue(String propertyName, Class<T> propertyType) {
        if (propertyType == Config.class) {
            return config.get().get(propertyName).as(propertyType).get();
        }
        //first iterate over mp sources, than use config
        return findInMpSources(propertyName)
                .map(value -> convert(propertyType, value))
                .orElseGet(() -> config.get().get(propertyName).as(propertyType).get());
    }

    private Object findArrayValue(String propertyName, Class<?> element) {
        // there should not be io.helidon.Config[]
        return findInMpSources(propertyName)
                .map(value -> asArray(value, element))
                .orElseGet(() -> {
                    Config arrayConfig = config.get().get(propertyName);
                    if (arrayConfig.isLeaf()) {
                        return asArray(arrayConfig.asString().get(), element);
                    }
                    List<?> objects = arrayConfig.asList(element).get();
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
    public <T> T value(String propertyName, Class<T> propertyType, T defaultValue) {
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
    public <T> T valueWithDefault(String propertyName, Class<T> propertyType, String defaultValue) {
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

        // Use a local converter for this class if we have one
        final Converter<?> converter = converters.get(type);
        if (null != converter) {
            return (T) converter.convert(value);
        } else {
            // If the request is for a String, we're done
            if (type == String.class) {
                return (T) value;
            } else if (type.isArray()) {
                // Recurse
                return (T) asArray(value, type.getComponentType());
            } else {
                // Ask helidon config to do appropriate conversion (built-in, implicit and classpath mappers)
                final Config c = helidonConverter();
                try {
                    return c.convert(type, value);
                } catch (ConfigMappingException e) {
                    throw new IllegalArgumentException("Failed to convert " + value + " to " + type.getName(), e);
                }
            }
        }
    }

    private Config helidonConverter() {
        Config converter = helidonConverter.get();
        if (converter == null) {
            converter = Config.builder()
                             .disableSystemPropertiesSource()
                             .disableFilterServices()
                             .disableEnvironmentVariablesSource()
                             .sources(ConfigSources.empty())
                             .build();
            helidonConverter.set(converter);
        }
        return converter;
    }

    /**
     * Get an instance of Helidon config (a tree structure) rather than the microprofile config.
     *
     * @return config instance that has the same properties as this instance
     */
    public Config helidonConfig() {
        // I need to create a config based on this config instance
        return Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .sources(ConfigSources.create(asMap()))
                .build();
    }

    /**
     * Get all properties of this config as a map.
     *
     * @return map where keys are configuration keys and values are associated string values
     */
    public Map<String, String> asMap() {
        // config from helidon config instance
        Map<String, String> map = new HashMap<>(config.get().asMap().get());
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
        return "microprofileConfig: " + config;
    }
}
