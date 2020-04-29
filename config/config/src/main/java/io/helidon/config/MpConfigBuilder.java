/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;

import io.helidon.common.serviceloader.Priorities;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Configuration builder.
 */
public class MpConfigBuilder implements ConfigBuilder {
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final String UNDERSCORE = "_";

    private final BuilderImpl delegate = new BuilderImpl();

    private boolean useDefaultSources = false;
    private boolean useDiscoveredSources = false;
    private boolean useDiscoveredConverters = false;

    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    private List<OrdinalSource> sources = new LinkedList<>();
    private List<OrdinalConverter> converters = new LinkedList<>();

    private boolean pureMp = true;

    MpConfigBuilder() {
        delegate.disableSystemPropertiesSource();
        delegate.disableEnvironmentVariablesSource();
        delegate.disableSourceServices();
        delegate.disableMpMapperServices();
    }

    @Override
    public ConfigBuilder addDefaultSources() {
        delegate.mpAddDefaultSources();
        useDefaultSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        delegate.mpAddDiscoveredSources();
        useDiscoveredSources = true;
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        delegate.mpAddDiscoveredConverters();
        useDiscoveredConverters = true;
        return this;
    }

    @Override
    public ConfigBuilder forClassLoader(ClassLoader loader) {
        delegate.mpForClassLoader(loader);
        this.classLoader = loader;
        return this;
    }

    @Override
    public ConfigBuilder withSources(ConfigSource... sources) {
        delegate.mpWithSources(sources);
        for (ConfigSource source : sources) {
            this.sources.add(new OrdinalSource(source));
        }
        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> aClass, int ordinal, Converter<T> converter) {
        delegate.mpWithConverter(aClass, ordinal, converter);
        this.converters.add(new OrdinalConverter(converter, aClass, ordinal));
        return this;
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        delegate.mpWithConverters(converters);
        for (Converter<?> converter : converters) {
            this.converters.add(new OrdinalConverter(converter));
        }
        return this;
    }

    @Override
    public Config build() {
        if (pureMp) {
            if (useDefaultSources) {
                sources.add(new OrdinalSource(new MpSystemPropertiesSource(), 400));
                sources.add(new OrdinalSource(new MpEnvironmentVariablesSource(), 300));
                // microprofile-config.properties
                try {
                    classLoader.getResources("META-INF/microprofile-config.properties")
                            .asIterator()
                            .forEachRemaining(it -> {
                                sources.add(new OrdinalSource(new MpPropertiesSource(it)));
                            });
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read microprofile-config.properties from classpath");
                }
            }

            // built-in converters
            converters.add(new OrdinalConverter(MpConfigBuilder::toBoolean, Boolean.class, 1));
            converters.add(new OrdinalConverter(MpConfigBuilder::toBoolean, Boolean.TYPE, 1));
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

            return new MpConfig(sources, converters);
        } else {
            return delegate.build();
        }
    }

    ConfigBuilder metaConfig(io.helidon.config.Config metaConfig) {
        pureMp = false;
        delegate.config(metaConfig);
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

    private static Boolean toBoolean(String stringValue) {
        final String lower = stringValue.toLowerCase();
        // according to microprofile config specification (section Built-in Converters)
        switch (lower) {
        case "true":
        case "1":
        case "yes":
        case "y":
        case "on":
            return true;
        default:
            return false;
        }
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

    private static class MpSystemPropertiesSource implements ConfigSource {
        private final Properties props;

        private MpSystemPropertiesSource() {
            this.props = System.getProperties();
        }

        @Override
        public Map<String, String> getProperties() {
            Set<String> strings = props.stringPropertyNames();

            Map<String, String> result = new HashMap<>();
            strings.forEach(it -> result.put(it, props.getProperty(it)));
            return result;
        }

        @Override
        public String getValue(String propertyName) {
            return props.getProperty(propertyName);
        }

        @Override
        public String getName() {
            return "System Properties";
        }
    }

    private static class MpEnvironmentVariablesSource implements ConfigSource {
        private final Map<String, String> env;

        private MpEnvironmentVariablesSource() {
            this.env = System.getenv();
            System.out.println("Setting up evn with: " + env);
        }

        @Override
        public Map<String, String> getProperties() {
            return env;
        }

        @Override
        public String getValue(String propertyName) {
            // According to the spec, we have three ways of looking for a property
            // 1. Exact match
            String result = env.get(propertyName);
            if (null != result) {
                System.out.println("Returning " + result + " for " + propertyName);
                return result;
            }
            // 2. replace non alphanumeric characters with _
            String rule2 = rule2(propertyName);
            result = env.get(rule2);
            if (null != result) {
                System.out.println("Returning " + result + " for " + rule2);
                return result;
            }
            // 3. replace same as above, but uppercase
            String rule3 = rule2.toUpperCase();
            result = env.get(rule3);
            System.out.println("Returning " + result + " for " + rule3);
            return result;
        }

        @Override
        public String getName() {
            return "Environment Variables";
        }

        /**
         * Rule #2 states: Replace each character that is neither alphanumeric nor _ with _ (i.e. com_ACME_size).
         *
         * @param propertyName name of property as requested by user
         * @return name of environment variable we look for
         */
        private static String rule2(String propertyName) {
            return DISALLOWED_CHARS.matcher(propertyName).replaceAll(UNDERSCORE);
        }
    }

    private class MpPropertiesSource implements ConfigSource {
        private final Map<String, String> props = new HashMap<>();
        private final String name;

        private MpPropertiesSource(URL it) {
            this.name = it.toString();

            try {
                URLConnection urlConnection = it.openConnection();
                try (InputStream inputStream = urlConnection.getInputStream()) {
                    Properties properties = new Properties();
                    properties.load(inputStream);

                    properties.stringPropertyNames()
                            .forEach(prop -> props.put(prop, properties.getProperty(prop)));
                }
            } catch (Exception e) {
                throw new ConfigException("Failed to load ", e);
            }

        }

        @Override
        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(props);
        }

        @Override
        public String getValue(String propertyName) {
            return props.get(propertyName);
        }

        @Override
        public String getName() {
            return "Properties: " + name;
        }
    }
}
