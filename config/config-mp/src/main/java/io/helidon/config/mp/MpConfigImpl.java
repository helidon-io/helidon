/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.mp.spi.MpConfigFilter;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Implementation of the basic MicroProfile {@link org.eclipse.microprofile.config.Config} API.
 */
@Deprecated
public class MpConfigImpl implements Config {
    private static final Logger LOGGER = Logger.getLogger(MpConfigImpl.class.getName());
    // for references resolving
    // matches string between ${ } with a negative lookbehind if there is not backslash
    private static final String REGEX_REFERENCE = "(?<!\\\\)\\$\\{([^}]+)\\}";
    private static final Pattern PATTERN_REFERENCE = Pattern.compile(REGEX_REFERENCE);
    // for encoding backslashes
    // matches a backslash with a positive lookahead if it is the backslash that encodes ${}
    private static final String REGEX_BACKSLASH = "\\\\(?=\\$\\{([^}]+)\\})";
    private static final Pattern PATTERN_BACKSLASH = Pattern.compile(REGEX_BACKSLASH);
    // I only care about unresolved key happening within the same thread
    private static final ThreadLocal<Set<String>> UNRESOLVED_KEYS = ThreadLocal.withInitial(HashSet::new);

    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");
    private static final Pattern ESCAPED_COMMA_PATTERN = Pattern.compile("\\,", Pattern.LITERAL);

    private final List<ConfigSource> sources = new LinkedList<>();
    private final HashMap<Class<?>, Converter<?>> converters = new LinkedHashMap<>();
    private final boolean valueResolving;
    private final List<MpConfigFilter> filters = new ArrayList<>();

    MpConfigImpl(List<ConfigSource> sources,
                 HashMap<Class<?>, Converter<?>> converters,
                 List<MpConfigFilter> filters) {
        this.sources.addAll(sources);
        this.converters.putAll(converters);
        this.converters.putIfAbsent(String.class, value -> value);

        this.valueResolving = getOptionalValue("helidon.config.value-resolving.enabled", Boolean.class)
                .orElse(true);

        // we need to initialize the filters first, before we set up filters
        filters.forEach(it -> {
            // initialize filter with filters with higher priority already in place
            it.init(this);
            // and then add it to the list of active filters
            // do not do this first, as we would end up in using an uninitialized filter
            this.filters.add(it);
        });
    }

    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        return getOptionalValue(propertyName, propertyType)
                .orElseThrow(() -> new NoSuchElementException("Property \"" + propertyName + "\" is not available in "
                                                                      + "configuration"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        // let's resolve arrays
        if (propertyType.isArray()) {
            Class<?> componentType = propertyType.getComponentType();
            // first try to see if we have a direct value
            Optional<String> optionalValue = getOptionalValue(propertyName, String.class);
            if (optionalValue.isPresent()) {
                return Optional.of((T) toArray(propertyName, optionalValue.get(), componentType));
            }

            /*
             we also support indexed value
             e.g. for key "my.list" you can have both:
             my.list=12,13,14
             or (not and):
             my.list.0=12
             my.list.1=13
             */

            String indexedConfigKey = propertyName + ".0";
            optionalValue = getOptionalValue(indexedConfigKey, String.class);
            if (optionalValue.isPresent()) {
                List<Object> result = new LinkedList<>();

                // first element is already in
                result.add(convert(indexedConfigKey, componentType, optionalValue.get()));

                // hardcoded limit to lists of 1000 elements
                for (int i = 1; i < 1000; i++) {
                    indexedConfigKey = propertyName + "." + i;
                    optionalValue = getOptionalValue(indexedConfigKey, String.class);
                    if (optionalValue.isPresent()) {
                        result.add(convert(indexedConfigKey, componentType, optionalValue.get()));
                    } else {
                        // finish the iteration on first missing index
                        break;
                    }
                }
                Object array = Array.newInstance(componentType, result.size());
                for (int i = 0; i < result.size(); i++) {
                    Object component = result.get(i);
                    Array.set(array, i, component);
                }
                return Optional.of((T) array);
            } else {
                return Optional.empty();
            }
        } else {
            return getStringValue(propertyName)
                    .flatMap(it -> applyFilters(propertyName, it))
                    .map(it -> convert(propertyName, propertyType, it));
        }
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ConfigSource source : sources) {
            names.addAll(source.getPropertyNames());
        }
        return names;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Return the {@link Converter} used by this instance to produce instances of the specified type from string values.
     *
     * This method is from a future version of MP Config specification and may changed before it
     * is released. It is nevertheless needed to process annotations with default values.
     *
     * @param <T> the conversion type
     * @param forType the type to be produced by the converter
     * @return an {@link java.util.Optional} containing the converter, or empty if no converter is available for the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
        return converters.entrySet()
                .stream()
                .filter(it -> forType.isAssignableFrom(it.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .map(it -> (Converter<T>) it)
                .or(() -> findImplicit(forType));
    }

    /**
     * Convert a String to a specific type.
     * This is a helper method to allow for processing of default values that cannot be typed (e.g. in annotations).
     *
     * @param propertyName name of the property, used for error messages
     * @param type  type of the property
     * @param value String value (may be null)
     * @param <T>   type
     * @return instance of the correct type, may return null in case null was provided and converter did not do this
     * @throws IllegalArgumentException in case the String provided cannot be converted to the type expected
     */
    private <T> T convert(String propertyName, Class<T> type, String value) {
        try {
            return findConverter(type)
                    .convert(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert property \""
                                                       + propertyName
                                                       + "\" from its value \""
                                                       + value
                                                       + "\" to "
                                                       + type.getName(),
                                               e);
        }
    }

    private Optional<String> getStringValue(String propertyName) {
        for (ConfigSource source : sources) {
            String value = source.getValue(propertyName);

            if (null == value) {
                // not in this one
                continue;
            }

            LOGGER.finest("Found property " + propertyName + " in source " + source.getName());
            return Optional.of(resolveReferences(propertyName, value));
        }

        return Optional.empty();
    }

    private Optional<String> applyFilters(String propertyName, String stringValue) {
        String result = stringValue;

        for (MpConfigFilter filter : filters) {
            result = filter.apply(propertyName, result);
        }

        return Optional.ofNullable(result);
    }

    private Object toArray(String propertyName, String stringValue, Class<?> componentType) {
        String[] values = toArray(stringValue);

        Object array = Array.newInstance(componentType, values.length);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            Array.set(array, i, convert(propertyName, componentType, value));
        }

        return array;
    }

    private String resolveReferences(String key, String value) {
        if (!valueResolving) {
            return value;
        }
        if (!UNRESOLVED_KEYS.get().add(key)) {
            UNRESOLVED_KEYS.get().clear();
            throw new IllegalStateException("Recursive resolving of references for key " + key + ", value: " + value);
        }
        try {
            return format(value);
        } catch (NoSuchElementException e) {
            LOGGER.log(Level.FINER, e, () -> String.format("Reference for key %s not found. Value: %s", key, value));
            return value;
        } finally {
            UNRESOLVED_KEYS.get().remove(key);
        }
    }

    private String format(String value) {
        Matcher m = PATTERN_REFERENCE.matcher(value);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String propertyName = m.group(1);
            m.appendReplacement(sb,
                                Matcher.quoteReplacement(getOptionalValue(propertyName, String.class)
                                                                 .orElseGet(() -> "${" + propertyName + "}")));
        }
        m.appendTail(sb);
        // remove all backslash that encodes ${...}
        m = PATTERN_BACKSLASH.matcher(sb.toString());

        return m.replaceAll("");
    }

    @SuppressWarnings("unchecked")
    <T> Converter<T> findConverter(Class<T> type) {
        Converter<?> converter = converters.get(type);
        if (null != converter) {
            return (Converter<T>) converter;
        }

        return getConverter(type)
                .orElseGet(() -> new FailingConverter<>(type));
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<Converter<T>> findImplicit(Class<T> type) {
        // enums must be explicitly supported
        if (Enum.class.isAssignableFrom(type)) {
            return Optional.of(value -> {
                Class<? extends Enum> enumClass = (Class<? extends Enum>) type;
                return (T) Enum.valueOf(enumClass, value);
            });
        }
        // any class that has a "public static T method()"
        Optional<Method> method = findMethod(type, "of", String.class)
                .or(() -> findMethod(type, "valueOf", String.class))
                .or(() -> findMethod(type, "parse", CharSequence.class))
                .or(() -> findMethod(type, "parse", String.class));

        if (method.isPresent()) {
            Method m = method.get();
            return Optional.of(value -> {
                try {
                    return (T) m.invoke(null, value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to convert to " + type.getName() + " using a static method", e);
                }
            });
        }

        // constructor with a single string parameter
        try {
            Constructor<T> constructor = type.getConstructor(String.class);
            if (constructor.canAccess(null)) {
                return Optional.of(value -> {
                    try {
                        return constructor.newInstance(value);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to convert to " + type.getName() + " using a constructor", e);
                    }
                });
            } else {
                LOGGER.finest("Constructor with String parameter is not accessible on type " + type);
            }
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.FINEST, "There is no public constructor with string parameter on class " + type.getName(), e);
        }

        return Optional.empty();
    }

    private Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method result = type.getDeclaredMethod(name, parameterTypes);
            if (!result.canAccess(null)) {
                LOGGER.finest(() -> "Method " + name + "(" + Arrays
                        .toString(parameterTypes) + ") is not accessible on class " + type.getName());
                return Optional.empty();
            }
            if (!Modifier.isStatic(result.getModifiers())) {
                LOGGER.finest(() -> "Method " + name + "(" + Arrays
                        .toString(parameterTypes) + ") is not static on class " + type.getName());
                return Optional.empty();
            }

            return Optional.of(result);
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.FINEST,
                       "Method " + name + "(" + Arrays.toString(parameterTypes) + ") is not avilable on class " + type.getName(),
                       e);
            return Optional.empty();
        }
    }

    HashMap<Class<?>, Converter<?>> converters() {
        return converters;
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = ESCAPED_COMMA_PATTERN.matcher(value).replaceAll(Matcher.quoteReplacement(","));
        }
        return values;
    }

    private static class FailingConverter<T> implements Converter<T> {
        private final Class<T> type;

        private FailingConverter(Class<T> type) {
            this.type = type;
        }

        @Override
        public T convert(String value) {
            throw new IllegalArgumentException("Cannot convert \"" + value + "\" to type " + type.getName());
        }
    }
}
