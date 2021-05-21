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

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.common.serviceloader.Priorities;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.spi.MpConfigFilter;
import io.helidon.config.yaml.YamlMpConfigSource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

import static io.helidon.config.mp.MpMetaConfig.MetaConfigSource;

/**
 * Configuration builder.
 */
@Deprecated
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

    void mpMetaConfig(io.helidon.config.Config meta) {
        meta.get("add-discovered-sources")
                .asBoolean()
                .filter(it -> it)
                .ifPresent(it -> addDiscoveredSources());

        meta.get("add-discovered-converters")
                .asBoolean()
                .filter(it -> it)
                .ifPresent(it -> addDiscoveredConverters());

        meta.get("add-default-sources")
                .asBoolean()
                .filter(it -> it)
                .ifPresent(it -> addDefaultSources());

        meta.get("sources")
                .asNodeList()
                .ifPresent(this::processMetaSources);
    }

    private void processMetaSources(List<io.helidon.config.Config> configs) {
        for (io.helidon.config.Config config : configs) {
            String type = config.get("type").asString()
                    .orElseThrow(() -> new ConfigException("Meta configuration sources must have a \"type\" property defined"));
            // in MP, we have a hardcoded list of supported configuration source types
            List<ConfigSource> delegates;
            switch (type) {
            case "system-properties":
                delegates = List.of(MpConfigSources.systemProperties());
                break;
            case "environment-variables":
                delegates = List.of(MpConfigSources.environmentVariables());
                break;
            case "properties":
                delegates = propertiesSource(config);
                break;
            case "yaml":
                delegates = yamlSource(config);
                break;
            default:
                throw new ConfigException("Meta configuration source type \"" + type + "\" is not supported. Use on of: "
                                                  + "system-properties, environment-variables, properties, yaml");
            }
            boolean shouldCount = delegates.size() > 1;
            int counter = 0;

            for (ConfigSource delegate : delegates) {
                MetaConfigSource.Builder builder = MetaConfigSource.builder()
                        .delegate(delegate);

                config.get("ordinal").asInt().ifPresent(builder::ordinal);
                ConfigValue<String> name = config.get("name").asString();
                if (shouldCount) {
                    if (name.isPresent()) {
                        // multiple instances - count them
                        builder.name(name.get() + "_" + counter++);
                    }
                } else {
                    name.ifPresent(builder::name);
                }

                withSources(builder.build());
            }
        }
    }

    private List<ConfigSource> propertiesSource(io.helidon.config.Config config) {
        return sourceFromMeta(config,
                              MpConfigSources::create,
                              MpConfigSources::classPath,
                              MpConfigSources::create);
    }

    private List<ConfigSource> yamlSource(io.helidon.config.Config config) {
        return sourceFromMeta(config,
                              YamlMpConfigSource::create,
                              YamlMpConfigSource::classPath,
                              YamlMpConfigSource::create);
    }

    private List<ConfigSource> sourceFromMeta(io.helidon.config.Config config,
                                              Function<Path, ConfigSource> fromPath,
                                              Function<String, List<ConfigSource>> fromClasspath,
                                              Function<URL, ConfigSource> fromUrl) {

        boolean optional = config.get("optional").asBoolean().orElse(false);

        String location;
        Exception cause = null;

        ConfigValue<Path> pathConfig = config.get("path").as(Path.class);
        if (pathConfig.isPresent()) {
            Path path = pathConfig.get();
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return List.of(fromPath.apply(path));
            }
            location = "path " + path.toAbsolutePath();
        } else {
            ConfigValue<String> classpathConfig = config.get("classpath").as(String.class);
            if (classpathConfig.isPresent()) {
                String classpath = classpathConfig.get();
                List<ConfigSource> sources = fromClasspath.apply(classpath);
                if (!sources.isEmpty()) {
                    return sources;
                }
                location = "classpath " + classpath;
            } else {
                ConfigValue<URL> urlConfig = config.get("url").as(URL.class);
                if (urlConfig.isPresent()) {
                    URL url = urlConfig.get();
                    try {
                        return List.of(fromUrl.apply(url));
                    } catch (ConfigException e) {
                        location = "url " + url;
                        cause = e;
                    }
                } else {
                    throw new ConfigException("MP meta configuration does not contain config source location. Node: " + config
                            .key());
                }
            }
        }

        if (optional) {
            return List.of();
        }
        String message = "Meta configuration could not find non-optional config source on " + location;
        if (cause == null) {
            throw new ConfigException(message);
        } else {
            throw new ConfigException(message, cause);
        }
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
        // built-in converters - required by specification
        addBuiltIn(converters, Boolean.class, ConfigMappers::toBoolean);
        addBuiltIn(converters, Boolean.TYPE, ConfigMappers::toBoolean);
        addBuiltIn(converters, Byte.class, Byte::parseByte);
        addBuiltIn(converters, Byte.TYPE, Byte::parseByte);
        addBuiltIn(converters, Short.class, Short::parseShort);
        addBuiltIn(converters, Short.TYPE, Short::parseShort);
        addBuiltIn(converters, Integer.class, Integer::parseInt);
        addBuiltIn(converters, Integer.TYPE, Integer::parseInt);
        addBuiltIn(converters, Long.class, Long::parseLong);
        addBuiltIn(converters, Long.TYPE, Long::parseLong);
        addBuiltIn(converters, Float.class, Float::parseFloat);
        addBuiltIn(converters, Float.TYPE, Float::parseFloat);
        addBuiltIn(converters, Double.class, Double::parseDouble);
        addBuiltIn(converters, Double.TYPE, Double::parseDouble);
        addBuiltIn(converters, Character.class, MpConfigBuilder::toChar);
        addBuiltIn(converters, Character.TYPE, MpConfigBuilder::toChar);
        addBuiltIn(converters, Class.class, MpConfigBuilder::toClass);

        // built-in converters - Helidon
        //javax.math
        addBuiltIn(converters, BigDecimal.class, ConfigMappers::toBigDecimal);
        addBuiltIn(converters, BigInteger.class, ConfigMappers::toBigInteger);
        //java.time
        addBuiltIn(converters, Duration.class, ConfigMappers::toDuration);
        addBuiltIn(converters, Period.class, ConfigMappers::toPeriod);
        addBuiltIn(converters, LocalDate.class, ConfigMappers::toLocalDate);
        addBuiltIn(converters, LocalDateTime.class, ConfigMappers::toLocalDateTime);
        addBuiltIn(converters, LocalTime.class, ConfigMappers::toLocalTime);
        addBuiltIn(converters, ZonedDateTime.class, ConfigMappers::toZonedDateTime);
        addBuiltIn(converters, ZoneId.class, ConfigMappers::toZoneId);
        addBuiltIn(converters, ZoneOffset.class, ConfigMappers::toZoneOffset);
        addBuiltIn(converters, Instant.class, ConfigMappers::toInstant);
        addBuiltIn(converters, OffsetTime.class, ConfigMappers::toOffsetTime);
        addBuiltIn(converters, OffsetDateTime.class, ConfigMappers::toOffsetDateTime);
        addBuiltIn(converters, YearMonth.class, YearMonth::parse);
        //java.io
        addBuiltIn(converters, File.class, MpConfigBuilder::toFile);
        //java.nio
        addBuiltIn(converters, Path.class, MpConfigBuilder::toPath);
        addBuiltIn(converters, Charset.class, ConfigMappers::toCharset);
        //java.net
        addBuiltIn(converters, URI.class, ConfigMappers::toUri);
        addBuiltIn(converters, URL.class, ConfigMappers::toUrl);
        //java.util
        addBuiltIn(converters, Pattern.class, ConfigMappers::toPattern);
        addBuiltIn(converters, UUID.class, ConfigMappers::toUUID);

        // obsolete stuff
        // noinspection UseOfObsoleteDateTimeApi
        addBuiltIn(converters, Date.class, ConfigMappers::toDate);
        // noinspection UseOfObsoleteDateTimeApi
        addBuiltIn(converters, Calendar.class, ConfigMappers::toCalendar);
        // noinspection UseOfObsoleteDateTimeApi
        addBuiltIn(converters, GregorianCalendar.class, ConfigMappers::toGregorianCalendar);
        // noinspection UseOfObsoleteDateTimeApi
        addBuiltIn(converters, TimeZone.class, ConfigMappers::toTimeZone);
        // noinspection UseOfObsoleteDateTimeApi
        addBuiltIn(converters, SimpleTimeZone.class, ConfigMappers::toSimpleTimeZone);

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

    private <T> void addBuiltIn(List<OrdinalConverter> converters, Class<T> clazz, Converter<T> converter) {
        converters.add(new OrdinalConverter(converter, clazz, 1));
    }

    ConfigBuilder metaConfig(io.helidon.config.Config metaConfig) {
        io.helidon.config.Config helidonConfig = io.helidon.config.Config.builder()
                .config(metaConfig)
                .build();
        this.sources.add(new OrdinalSource(MpConfigSources.create(helidonConfig)));
        return this;
    }

    private static File toFile(String value) {
        return new File(value);
    }

    private static Path toPath(String value) {
        return Paths.get(value);
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
