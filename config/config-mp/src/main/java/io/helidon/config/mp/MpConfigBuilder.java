/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.ServiceLoader;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.helidon.common.Builder;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.common.serviceloader.Priorities;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappers;
import io.helidon.config.ConfigValue;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.mp.spi.MpConfigFilter;
import io.helidon.config.mp.spi.MpConfigSourceProvider;
import io.helidon.config.mp.spi.MpMetaConfigProvider;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

import static io.helidon.config.mp.MpMetaConfig.MetaConfigSource;

/**
 * Configuration builder.
 */
@Configured(prefix = "mp.config", root = true)
class MpConfigBuilder implements Builder<MpConfigBuilder, Config>, ConfigBuilder {
    private static final Logger LOGGER = Logger.getLogger(MpConfigBuilder.class.getName());
    private static final String DEFAULT_CONFIG_SOURCE = "META-INF/microprofile-config.properties";

    private static final Map<String, MpMetaConfigProvider> MP_META_PROVIDERS;
    static {
        List<MpMetaConfigProvider> mpMetaConfigProviders =
                HelidonServiceLoader.builder(ServiceLoader.load(MpMetaConfigProvider.class))
                        .addService(new MpEnvironmentVariablesMetaConfigProvider())
                        .addService(new MpSystemPropertiesMetaConfigProvider())
                        .addService(new MpPropertiesMetaConfigProvider())
                        .build()
                        .asList();

        Map<String, MpMetaConfigProvider> theMap = new HashMap<>();
        // ordered by priority
        for (MpMetaConfigProvider mpMetaConfigProvider : mpMetaConfigProviders) {
            for (String supportedType : mpMetaConfigProvider.supportedTypes()) {
                theMap.putIfAbsent(supportedType, mpMetaConfigProvider);
            }
        }
        MP_META_PROVIDERS = Map.copyOf(theMap);
    }


    private final List<OrdinalSource> sources = new LinkedList<>();
    private final List<OrdinalConverter> converters = new LinkedList<>();

    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    private boolean useDefaultSources = false;
    private boolean useDiscoveredSources = false;
    private boolean useDiscoveredConverters = false;
    private String profile;

    MpConfigBuilder() {
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
        meta.get("profile")
                .asString()
                .ifPresent(this::profile);

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
            MpMetaConfigProvider mpMetaConfigProvider = MP_META_PROVIDERS.get(type);
            if (mpMetaConfigProvider == null) {
                throw new ConfigException("Wrong meta configuration, type " + type
                        + " not supported, only supporting: " + MP_META_PROVIDERS.keySet());
            }

            List<? extends ConfigSource> delegates = mpMetaConfigProvider.create(type, config, profile);

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

    @Override
    public Config build() {
        // the build method MUST NOT modify builder state, as it may be called more than once
        // there are three lists used by the configuration:
        //  sources
        //  converters
        //  filters
        List<OrdinalSource> ordinalSources = new LinkedList<>(sources);
        List<OrdinalConverter> ordinalConverters = new LinkedList<>(converters);
        List<MpConfigFilter> targetFilters = HelidonServiceLoader.create(ServiceLoader.load(MpConfigFilter.class))
                .asList();

        /*
         Converters
         */
        addBuiltInConverters(ordinalConverters);
        if (useDiscoveredConverters) {
            addDiscoveredConverters(ordinalConverters);
        }

        /*
         Config sources
         */
        if (useDefaultSources) {
            addDefaultSources(ordinalSources);
        }
        if (useDiscoveredSources) {
            addDiscoveredSources(ordinalSources);
        }

        // now it is from lowest to highest
        ordinalSources.sort(Comparator.comparingInt(o -> o.ordinal));
        ordinalConverters.sort(Comparator.comparingInt(o -> o.ordinal));

        // revert to have the first one the most significant
        Collections.reverse(ordinalSources);
        Collections.reverse(ordinalConverters);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("The following config sources are used (ordered): " + ordinalSources);
        }

        List<ConfigSource> targetSources = new LinkedList<>();
        HashMap<Class<?>, Converter<?>> targetConverters = new HashMap<>();

        ordinalSources.forEach(ordinal -> targetSources.add(ordinal.source));
        ordinalConverters.forEach(ordinal -> targetConverters.putIfAbsent(ordinal.type, ordinal.converter));

        MpConfigImpl result = new MpConfigImpl(targetSources, targetConverters, targetFilters, profile);

        // if we already have a profile configured, we have loaded it and can safely return
        if (profile != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Built MP config for profile " + profile);
            }
            return result;
        }

        // let's see if there is a profile configured
        String configuredProfile = result.getOptionalValue("mp.config.profile", String.class).orElse(null);

        // nope, return the result
        if (configuredProfile == null) {
            LOGGER.fine("Built MP config with no profile");
            return result;
        } else {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("MP profile configured, rebuilding: " + configuredProfile);
            }
        }

        // yes, update it and re-build with profile information
        profile(configuredProfile);

        return build();
    }

    /**
     * Configure an explicit profile name. Profile is used to load configuration (when default sources are enabled) from
     * {@code microprofile-config-${profile}.properties} and to use properties named {@code %${profile}.propertyName}
     * before the actual property name.
     *
     * @param profile name of the profile, such as {@code dev, test}
     * @return updated builder instance
     */
    @ConfiguredOption(key = "profile", type = String.class, description = "Configure an explicit profile name.")
    public MpConfigBuilder profile(String profile) {
        this.profile = profile;
        return this;
    }

    private void addDiscoveredSources(List<OrdinalSource> targetConfigSources) {
        ServiceLoader.load(ConfigSource.class)
                .forEach(it -> targetConfigSources.add(new OrdinalSource(it)));

        ServiceLoader.load(ConfigSourceProvider.class)
                .forEach(it -> it.getConfigSources(classLoader)
                        .forEach(source -> targetConfigSources.add(new OrdinalSource(source))));

        ServiceLoader.load(MpConfigSourceProvider.class)
                .forEach(it -> (profile == null ? it.getConfigSources(classLoader) : it.getConfigSources(classLoader, profile))
                        .forEach(source -> targetConfigSources.add(new OrdinalSource(source))));
    }

    private void addDiscoveredConverters(List<OrdinalConverter> targetConverters) {
        ServiceLoader.load(Converter.class)
                .forEach(it -> targetConverters.add(new OrdinalConverter(it)));
    }

    private void addDefaultSources(List<OrdinalSource> targetConfigSources) {
        if (useDefaultSources) {
            // add default sources - system properties, environment variables and microprofile-config.properties
            targetConfigSources.add(new OrdinalSource(MpConfigSources.systemProperties(), 400));
            targetConfigSources.add(new OrdinalSource(MpConfigSources.environmentVariables(), 300));
            // microprofile-config.properties
            if (profile == null) {
                MpConfigSources.classPath(classLoader, DEFAULT_CONFIG_SOURCE)
                        .stream()
                        .map(OrdinalSource::new)
                        .forEach(targetConfigSources::add);
            } else {
                MpConfigSources.classPath(classLoader, DEFAULT_CONFIG_SOURCE, profile)
                        .stream()
                        .map(OrdinalSource::new)
                        .forEach(targetConfigSources::add);
            }

            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("The following default config sources discovered: " + targetConfigSources);
            }
        }
    }

    private void addBuiltInConverters(List<OrdinalConverter> converters) {
        // built-in converters - required by specification
        addBuiltIn(converters, Boolean.class, ConfigMappers::toBoolean);
        addBuiltIn(converters, Byte.class, Byte::parseByte);
        addBuiltIn(converters, Short.class, Short::parseShort);
        addBuiltIn(converters, Integer.class, Integer::parseInt);
        addBuiltIn(converters, OptionalInt.class, MpConverters::toOptionalInt);
        addBuiltIn(converters, Long.class, Long::parseLong);
        addBuiltIn(converters, OptionalLong.class, MpConverters::toOptionalLong);
        addBuiltIn(converters, Float.class, Float::parseFloat);
        addBuiltIn(converters, Double.class, Double::parseDouble);
        addBuiltIn(converters, OptionalDouble.class, MpConverters::toOptionalDouble);
        addBuiltIn(converters, Character.class, MpConfigBuilder::toChar);
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

    private static class OrdinalSource {
        private final int ordinal;
        private final ConfigSource source;

        private OrdinalSource(ConfigSource source) {
            this.source = source;
            this.ordinal = findOrdinal(source, ConfigSource.DEFAULT_ORDINAL);
        }

        private OrdinalSource(ConfigSource source, int ordinal) {
            this.ordinal = findOrdinal(source, ordinal);
            this.source = source;
        }

        private static int findOrdinal(ConfigSource source, int defaultOrdinal) {
            int ordinal = source.getOrdinal();
            if (ordinal == ConfigSource.DEFAULT_ORDINAL) {
                return Priorities.find(source, defaultOrdinal);
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
            this.converter = new NullCheckingConverter<>(aClass, converter);
        }

        private OrdinalConverter(Converter<?> converter) {
            this(converter, getConverterType(converter.getClass()), Priorities.find(converter, 100));
        }

        @Override
        public String toString() {
            return type.getName() + "->" + converter;
        }
    }

    private static final class NullCheckingConverter<T> implements Converter<T> {
        private final Converter<T> delegate;
        private final Class<?> type;

        private NullCheckingConverter(Class<?> type, Converter<T> delegate) {
            this.delegate = delegate;
            this.type = type;
        }

        @Override
        public T convert(String value) throws IllegalArgumentException, NullPointerException {
            if (value == null) {
                throw new NullPointerException("Null not allowed in MP converters. Converter for type " + type.getName());
            }

            try {
                return delegate.convert(value);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot convert value", e);
            }
        }

        @Override
        public String toString() {
            return type.getName() + "->" + delegate;
        }
    }
}
