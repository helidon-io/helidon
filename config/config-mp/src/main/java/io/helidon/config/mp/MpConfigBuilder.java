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
 */

package io.helidon.config.mp;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.common.serviceloader.Priorities;
import io.helidon.config.ConfigMappers;
import io.helidon.config.mp.spi.MpConfigFilter;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Configuration builder.
 */
public class MpConfigBuilder implements ConfigBuilder {
    private boolean useDefaultSources = false;
    private boolean useDiscoveredSources = false;
    private boolean useDiscoveredConverters = false;

    private final List<OrdinalSource> sources = new LinkedList<>();
    private final List<OrdinalConverter> converters = new LinkedList<>();

    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    MpConfigBuilder() {
    }

    @Override
    public ConfigBuilder addDefaultSources() {
        useDefaultSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        useDiscoveredSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        useDiscoveredConverters = true;
        return this;
    }

    @Override
    public ConfigBuilder forClassLoader(ClassLoader loader) {
        this.classLoader = loader;
        return this;
    }

    @Override
    public ConfigBuilder withSources(ConfigSource... sources) {
        for (ConfigSource source : sources) {
            this.sources.add(new OrdinalSource(source));
        }
        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> aClass, int ordinal, Converter<T> converter) {
        this.converters.add(new OrdinalConverter(converter, aClass, ordinal));
        return this;
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        for (Converter<?> converter : converters) {
            this.converters.add(new OrdinalConverter(converter));
        }
        return this;
    }

    @Override
    public Config build() {
        if (useDefaultSources) {
            sources.add(new OrdinalSource(MpConfigSources.systemProperties(), 400));
            sources.add(new OrdinalSource(MpConfigSources.environmentVariables(), 300));
            // microprofile-config.properties
            MpConfigSources.classPath(classLoader, "META-INF/microprofile-config.properties")
                    .stream()
                    .map(OrdinalSource::new)
                    .forEach(sources::add);
        }
        // built-in converters
        converters.add(new OrdinalConverter(ConfigMappers::toBoolean, Boolean.class, 1));
        converters.add(new OrdinalConverter(ConfigMappers::toBoolean, Boolean.TYPE, 1));
        converters.add(new OrdinalConverter(Byte::parseByte, Byte.class, 1));
        converters.add(new OrdinalConverter(Byte::parseByte, Byte.TYPE, 1));
        converters.add(new OrdinalConverter(Short::parseShort, Short.class, 1));
        converters.add(new OrdinalConverter(Short::parseShort, Short.TYPE, 1));
        converters.add(new OrdinalConverter(Integer::parseInt, Integer.class, 1));
        converters.add(new OrdinalConverter(Integer::parseInt, Integer.TYPE, 1));
        converters.add(new OrdinalConverter(Long::parseLong, Long.class, 1));
        converters.add(new OrdinalConverter(Long::parseLong, Long.TYPE, 1));
        converters.add(new OrdinalConverter(Float::parseFloat, Float.class, 1));
        converters.add(new OrdinalConverter(Float::parseFloat, Float.TYPE, 1));
        converters.add(new OrdinalConverter(Double::parseDouble, Double.class, 1));
        converters.add(new OrdinalConverter(Double::parseDouble, Double.TYPE, 1));
        converters.add(new OrdinalConverter(MpConfigBuilder::toChar, Character.class, 1));
        converters.add(new OrdinalConverter(MpConfigBuilder::toChar, Character.TYPE, 1));
        converters.add(new OrdinalConverter(MpConfigBuilder::toClass, Class.class, 1));

        if (useDiscoveredConverters) {
            ServiceLoader.load(Converter.class)
                    .forEach(it -> converters.add(new OrdinalConverter(it)));
        }

        if (useDiscoveredSources) {
            ServiceLoader.load(ConfigSource.class)
                    .forEach(it -> sources.add(new OrdinalSource(it)));

            ServiceLoader.load(ConfigSourceProvider.class)
                    .forEach(it -> {
                        it.getConfigSources(classLoader)
                                .forEach(source -> sources.add(new OrdinalSource(source)));
                    });
        }

        // now it is from lowest to highest
        sources.sort(Comparator.comparingInt(o -> o.ordinal));
        converters.sort(Comparator.comparingInt(o -> o.ordinal));

        // revert to have the first one the most significant
        Collections.reverse(sources);
        Collections.reverse(converters);

        List<ConfigSource> sources = new LinkedList<>();
        HashMap<Class<?>, Converter<?>> converters = new HashMap<>();

        this.sources.forEach(ordinal -> sources.add(ordinal.source));
        this.converters.forEach(ordinal -> converters.putIfAbsent(ordinal.type, ordinal.converter));

        List<MpConfigFilter> filters = HelidonServiceLoader.create(ServiceLoader.load(MpConfigFilter.class))
                .asList();

        return new MpConfigImpl(sources, converters, filters);
    }

    ConfigBuilder metaConfig(io.helidon.config.Config metaConfig) {
        io.helidon.config.Config helidonConfig = io.helidon.config.Config.builder()
                .config(metaConfig)
                .build();
        this.sources.add(new OrdinalSource(MpConfigSources.create(helidonConfig)));
        return this;
    }

    private static Class<?> toClass(String stringValue) {
        try {
            return Class.forName(stringValue);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Failed to convert property " + stringValue + " to class", e);
        }
    }

    private static char toChar(String stringValue) {
        if (stringValue.length() != 1) {
            throw new IllegalArgumentException("The string to map must be a single character, but is: " + stringValue);
        }
        return stringValue.charAt(0);
    }

    private static class OrdinalSource {
        private final int ordinal;
        private final ConfigSource source;

        private OrdinalSource(ConfigSource source) {
            this.source = source;
            this.ordinal = findOrdinal(source);
        }

        private OrdinalSource(ConfigSource source, int ordinal) {
            this.ordinal = ordinal;
            this.source = source;
        }

        private static int findOrdinal(ConfigSource source) {
            int ordinal = source.getOrdinal();
            if (ordinal == ConfigSource.DEFAULT_ORDINAL) {
                return Priorities.find(source, ConfigSource.DEFAULT_ORDINAL);
            }
            return ordinal;
        }

        @Override
        public String toString() {
            return ordinal + " " + source.getName();
        }
    }

    private static class OrdinalConverter {
        private final int ordinal;
        private final Class<?> type;
        private final Converter<?> converter;

        private OrdinalConverter(Converter<?> converter, Class<?> aClass, int ordinal) {
            this.ordinal = ordinal;
            this.type = aClass;
            this.converter = converter;
        }

        private OrdinalConverter(Converter<?> converter) {
            this(converter, getConverterType(converter.getClass()), Priorities.find(converter, 100));
        }
    }

    private static Class<?> getConverterType(Class<?> converterClass) {
        Class<?> type = doGetType(converterClass);
        if (null == type) {
            throw new IllegalArgumentException("Converter " + converterClass + " must be a ParameterizedType.");
        }
        return type;
    }

    private static Class<?> doGetType(Class<?> clazz) {
        if (clazz.equals(Object.class)) {
            return null;
        }

        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if (pt.getRawType().equals(Converter.class)) {
                    Type[] typeArguments = pt.getActualTypeArguments();
                    if (typeArguments.length != 1) {
                        throw new IllegalStateException("Converter " + clazz + " must be a ParameterizedType.");
                    }
                    Type typeArgument = typeArguments[0];
                    if (typeArgument instanceof Class) {
                        return (Class<?>) typeArgument;
                    }
                    throw new IllegalStateException("Converter " + clazz + " must convert to a class, not " + typeArgument);
                }
            }
        }

        return doGetType(clazz.getSuperclass());
    }
}
