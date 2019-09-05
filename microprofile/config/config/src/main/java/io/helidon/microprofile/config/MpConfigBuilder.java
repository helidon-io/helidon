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

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Configuration builder.
 */
public class MpConfigBuilder implements ConfigBuilder {
    private static final Logger LOGGER = Logger.getLogger(MpConfigBuilder.class.getName());
    private static final int DEFAULT_ORDINAL = 100;
    private static final int BUILT_IN_ORDINAL = 1;
    private static final List<OrdinalConverter<?>> BUILT_IN_CONVERTERS = initBuiltInConverters();

    private final List<ConfigSource> mpConfigSources = new LinkedList<>();
    private final List<OrdinalConverter<?>> converters = new LinkedList<>();
    private final List<io.helidon.config.spi.ConfigSource> helidonConfigSources = new LinkedList<>();
    private final io.helidon.config.Config.Builder helidonConfigBuilder = io.helidon.config.Config.builder();

    private ClassLoader classLoader;
    private io.helidon.config.Config helidonConfig;

    MpConfigBuilder() {
        helidonConfigBuilder.disableEnvironmentVariablesSource();
        helidonConfigBuilder.disableSystemPropertiesSource();
        converters.addAll(BUILT_IN_CONVERTERS);
    }

    private static List<OrdinalConverter<?>> initBuiltInConverters() {
        List<OrdinalConverter<?>> result = new LinkedList<>();

        result.add(new OrdinalConverter<>(boolean.class, MpConfigBuilder::toBoolean, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Boolean.class, MpConfigBuilder::toBoolean, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(byte.class, Byte::parseByte, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Byte.class, Byte::parseByte, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(short.class, Short::parseShort, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Short.class, Short::parseShort, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(int.class, Integer::parseInt, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Integer.class, Integer::parseInt, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(long.class, Long::parseLong, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Long.class, Long::parseLong, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(float.class, Float::parseFloat, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Float.class, Float::parseFloat, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(double.class, Double::parseDouble, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Double.class, Double::parseDouble, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(char.class, ConfigMappers::toChar, BUILT_IN_ORDINAL));
        result.add(new OrdinalConverter<>(Character.class, ConfigMappers::toChar, BUILT_IN_ORDINAL));

        result.add(new OrdinalConverter<>(Class.class, ConfigMappers::toClass, BUILT_IN_ORDINAL));

        return result;
    }

    private static boolean toBoolean(final String value) {
        final String lower = value.toLowerCase();
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

    @Override
    public ConfigBuilder addDefaultSources() {
        // system properties
        mpConfigSources.add(new MpcSourceSystemProperties());
        helidonConfigSources.add(ConfigSources.systemProperties());

        // environment variables
        mpConfigSources.add(new MpcSourceEnvironmentVariables());
        helidonConfigSources.add(ConfigSources.environmentVariables());

        // /META-INF/microprofile-config.properties
        try {
            Enumeration<URL> resources = getClassLoader().getResources("META-INF/microprofile-config.properties");

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                mpConfigSources.add(MpcSourceUrl.create(url));
                helidonConfigSources.add(ConfigSources.url(url).build());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read microprofile-config.properties", e);
        }

        // application.yaml - helidon default
        io.helidon.config.spi.ConfigSource cs = ConfigSources.classpath("application.yaml").optional().build();
        helidonConfigSources.add(cs);
        cs = ConfigSources.file("application.yaml").optional().build();
        helidonConfigSources.add(cs);

        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        ServiceLoader.load(ConfigSource.class, getClassLoader())
                .forEach(this::addConfigSource);

        ServiceLoader.load(ConfigSourceProvider.class, getClassLoader())
                .forEach(csp -> csp.getConfigSources(getClassLoader())
                        .forEach(this::addConfigSource));

        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        ServiceLoader.load(Converter.class, getClassLoader())
                .forEach(this::addConverter);

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
            addConfigSource(source);
        }

        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> aClass, int ordinal, Converter<T> converter) {
        converters.add(new OrdinalConverter<>(aClass,
                                              converter,
                                              ordinal));
        return this;
    }

    /**
     * Set the Helidon config to be used as a "backend" for this MP config.
     *
     * @param config config instance to query if MP sources do not contain the key
     * @return modified builder
     */
    public MpConfigBuilder config(io.helidon.config.Config config) {
        this.helidonConfig = config;
        return this;
    }

    private void addConfigSource(ConfigSource source) {
        LOGGER.finest(() -> "Adding config source: " + source.getName() + " (" + source.getClass()
                .getName() + "), values: " + source.getProperties());

        mpConfigSources.add(source);
        helidonConfigSources.add(wrapSource(source));
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        for (Converter<?> converter : converters) {
            addConverter(converter);
        }
        return this;
    }

    @Override
    public Config build() {
        orderLists();

        Map<Class<?>, Function<io.helidon.config.Config, ?>> configMappers = new IdentityHashMap<>();

        converters.forEach(oc -> {
            final Class<?> type = oc.type;
            Function<io.helidon.config.Config, ?> mapper = config -> oc.converter.convert(config.asString().get());
            configMappers.put(type, mapper);
        });

        if (null == helidonConfig) {
            // only helidon config sources
            helidonConfigBuilder.sources(toSupplierList(helidonConfigSources));
            helidonConfigBuilder.addMapper(() -> configMappers);

            helidonConfig = helidonConfigBuilder.build();
        }

        Map<Class<?>, Converter<?>> converterMap = new HashMap<>();
        for (OrdinalConverter<?> converter : this.converters) {
            converterMap.put(converter.type, converter.converter);
        }

        return new MpConfig(helidonConfig, mpConfigSources, converterMap);
    }

    private void orderLists() {

        // Order lowest to highest so higher takes precedence when map is built
        mpConfigSources.sort(Comparator.comparingInt(ConfigSource::getOrdinal));

        // Order highest to lowest
        converters.sort(Comparator.comparingInt(OrdinalConverter::getOrdinal));
        Collections.reverse(mpConfigSources);
    }

    private List<Supplier<io.helidon.config.spi.ConfigSource>>
        toSupplierList(List<io.helidon.config.spi.ConfigSource> configSources) {

        return configSources.stream()
                .map(cs -> (Supplier<io.helidon.config.spi.ConfigSource>) () -> cs)
                .collect(Collectors.toList());

    }

    private ClassLoader getClassLoader() {
        return (null == classLoader ? Thread.currentThread().getContextClassLoader() : classLoader);
    }

    @SuppressWarnings("unchecked")
    private <T> void addConverter(Converter<T> converter) {
        Class<T> type = (Class<T>) getTypeOfConverter(converter.getClass());
        if (type == null) {
            throw new IllegalStateException("Converter " + converter.getClass() + " must be a ParameterizedType");
        }

        LOGGER.finest(() -> "Adding converter for type: " + type.getName() + " (" + converter.getClass().getName() + ")");

        converters.add(new OrdinalConverter<>(type,
                                              converter,
                                              findPriority(converter.getClass())));
    }

    private int findPriority(Class<?> aClass) {
        Priority priorityAnnot = aClass.getAnnotation(Priority.class);
        if (null != priorityAnnot) {
            return priorityAnnot.value();
        }

        return DEFAULT_ORDINAL;
    }

    private Type getTypeOfConverter(Class clazz) {
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
                    return typeArguments[0];
                }
            }
        }

        return getTypeOfConverter(clazz.getSuperclass());
    }

    private OrdinalConfigSource wrapSource(ConfigSource source) {
        io.helidon.config.spi.ConfigSource myCs = ConfigSources.create(source.getProperties()).build();

        return new OrdinalConfigSource(myCs, source.getOrdinal());
    }

    private static class OrdinalConverter<T> {
        private final Class<T> type;
        private final Converter converter;
        private final int ordinal;

        OrdinalConverter(Class<T> type, Converter converter, int ordinal) {
            this.type = type;
            this.converter = converter;
            this.ordinal = ordinal;
        }

        int getOrdinal() {
            return ordinal;
        }
    }

    private static final class OrdinalConfigSource implements io.helidon.config.spi.ConfigSource {
        private final io.helidon.config.spi.ConfigSource configSource;
        private final int ordinal;

        private OrdinalConfigSource(io.helidon.config.spi.ConfigSource configSource, int ordinal) {
            this.configSource = configSource;
            this.ordinal = ordinal;
        }

        @Override
        public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
            return configSource.load();
        }

        @Override
        public io.helidon.config.spi.ConfigSource get() {
            return configSource.get();
        }

        @Override
        public void init(ConfigContext context) {
            configSource.init(context);
        }

        @Override
        public String description() {
            return configSource.description();
        }

        @Override
        public Flow.Publisher<Optional<ConfigNode.ObjectNode>> changes() {
            return configSource.changes();
        }

        int getOrdinal() {
            return ordinal;
        }
    }
}
