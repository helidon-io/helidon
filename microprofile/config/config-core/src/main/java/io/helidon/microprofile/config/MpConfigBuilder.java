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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMapper;
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
    private static final List<OrdinalConverter<?>> BUILT_IN_CONVERTERS = initBuiltInConverters();

    private final List<ConfigSource> mpConfigSources = new LinkedList<>();

    private final List<OrdinalConverter<?>> converters = new LinkedList<>();
    private final List<io.helidon.config.spi.ConfigSource> helidonConfigSources = new LinkedList<>();
    private ClassLoader classLoader;
    private io.helidon.config.Config helidonConfig;
    private io.helidon.config.Config.Builder helidonConfigBuilder = io.helidon.config.Config.builder();

    MpConfigBuilder() {
        helidonConfigBuilder.disableEnvironmentVariablesSource();
        helidonConfigBuilder.disableSystemPropertiesSource();
        converters.addAll(BUILT_IN_CONVERTERS);
    }

    private static List<OrdinalConverter<?>> initBuiltInConverters() {
        List<OrdinalConverter<?>> result = new LinkedList<>();

        result.add(new OrdinalConverter<>(Boolean.class,
                                          value -> {
                                              String lower = value.toLowerCase();
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
                                          },
                                          DEFAULT_ORDINAL));

        result.add(new OrdinalConverter<>(YearMonth.class,
                                          YearMonth::parse,
                                          DEFAULT_ORDINAL));

        return result;
    }

    @Override
    public ConfigBuilder addDefaultSources() {
        // system properties
        mpConfigSources.add(new MpcSourceSystemProperties());

        // environment variables
        mpConfigSources.add(new MpcSourceEnvironmentVariables());

        // /META-INF/microprofile-config.properties
        try {
            Enumeration<URL> resources = getClassLoader().getResources("META-INF/microprofile-config.properties");

            while (resources.hasMoreElements()) {
                mpConfigSources.add(MpcSourceUrl.from(resources.nextElement()));
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
    @SuppressWarnings("unchecked")
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

        Map<Class<?>, ConfigMapper<?>> configMappers = new IdentityHashMap<>();

        Set<Class> converterClasses = new HashSet<>();

        // add built-in converters of Helidon config
        converterClasses.add(String.class);
        converterClasses.add(Byte.class);
        converterClasses.add(byte.class);
        converterClasses.add(Short.class);
        converterClasses.add(short.class);
        converterClasses.add(Integer.class);
        converterClasses.add(int.class);
        converterClasses.add(Long.class);
        converterClasses.add(long.class);
        converterClasses.add(Float.class);
        converterClasses.add(float.class);
        converterClasses.add(Double.class);
        converterClasses.add(double.class);
        converterClasses.add(Boolean.class);
        converterClasses.add(boolean.class);
        converterClasses.add(Character.class);
        converterClasses.add(char.class);
        converterClasses.add(Class.class);
        converterClasses.add(BigDecimal.class);
        converterClasses.add(BigInteger.class);
        converterClasses.add(Duration.class);
        converterClasses.add(Period.class);
        converterClasses.add(LocalDate.class);
        converterClasses.add(LocalDateTime.class);
        converterClasses.add(LocalTime.class);
        converterClasses.add(ZonedDateTime.class);
        converterClasses.add(ZoneId.class);
        converterClasses.add(ZoneOffset.class);
        converterClasses.add(Instant.class);
        converterClasses.add(OffsetTime.class);
        converterClasses.add(OffsetDateTime.class);
        converterClasses.add(File.class);
        converterClasses.add(Path.class);
        converterClasses.add(Charset.class);
        converterClasses.add(URI.class);
        converterClasses.add(URL.class);
        converterClasses.add(Pattern.class);
        converterClasses.add(UUID.class);
        converterClasses.add(Map.class);
        converterClasses.add(Properties.class);
        converterClasses.add(Date.class);
        converterClasses.add(Calendar.class);
        converterClasses.add(GregorianCalendar.class);
        converterClasses.add(TimeZone.class);
        converterClasses.add(SimpleTimeZone.class);

        converters.forEach(oc -> {
            Class<?> c = oc.type;
            ConfigMapper mapper = config -> oc.converter.convert(config.asString());
            configMappers.put(c, mapper);
            converterClasses.add(c);
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

        return new MpConfig(helidonConfig, mpConfigSources, converterClasses, converterMap);
    }

    private void orderLists() {
        mpConfigSources.sort(Comparator.comparingInt(ConfigSource::getOrdinal));
        converters.sort(Comparator.comparingInt(OrdinalConverter::getOrdinal));

        // correctly order from highest to lowest
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
        io.helidon.config.spi.ConfigSource myCs = ConfigSources.from(source.getProperties()).build();

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

    private static class OrdinalConfigSource implements io.helidon.config.spi.ConfigSource {
        private io.helidon.config.spi.ConfigSource configSource;
        private int ordinal;

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
