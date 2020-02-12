/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParsePosition;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods for converting configuration to Java types.
 * <p>
 * Note that this class defines many methods of the form {@code <type> to<type>(String)}
 * which are automatically registered with each {@link Config.Builder}.
 *
 * @see io.helidon.config.spi.ConfigMapperProvider
 */
public final class ConfigMappers {

    private static final Map<Class<?>, Function<Config, ?>> ESSENTIAL_MAPPERS = initEssentialMappers();
    static final Map<Class<?>, Function<Config, ?>> BUILT_IN_MAPPERS = initBuiltInMappers();

    private ConfigMappers() {
        throw new AssertionError("Instantiation not allowed.");
    }

    private static Map<Class<?>, Function<Config, ?>> initEssentialMappers() {
        Map<Class<?>, Function<Config, ?>> essentials = new HashMap<>();

        essentials.put(Config.class, (node) -> node);

        essentials.put(String.class, wrap(value -> value));

        essentials.put(OptionalInt.class, (node) -> {
            if (!node.exists()) {
                return OptionalInt.empty();
            }

            return OptionalInt.of(wrap(Integer::parseInt).apply(node));
        });

        essentials.put(OptionalLong.class, (node) -> {
            if (!node.exists()) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(wrap(Long::parseLong).apply(node));
        });

        essentials.put(OptionalDouble.class, (node) -> {
            if (!node.exists()) {
                return OptionalDouble.empty();
            }

            return OptionalDouble.of(wrap(Double::parseDouble).apply(node));
        });

        return Collections.unmodifiableMap(essentials);
    }

    static Map<Class<?>, Function<Config, ?>> essentialMappers() {
        return ESSENTIAL_MAPPERS;
    }

    private static Map<Class<?>, Function<Config, ?>> initBuiltInMappers() {
        Map<Class<?>, Function<Config, ?>> builtIns = new HashMap<>();

        //primitive types
        builtIns.put(Byte.class, wrap(ConfigMappers::toByte));
        builtIns.put(Short.class, wrap(ConfigMappers::toShort));
        builtIns.put(Integer.class, wrap(ConfigMappers::toInt));
        builtIns.put(Long.class, wrap(ConfigMappers::toLong));
        builtIns.put(Float.class, wrap(ConfigMappers::toFloat));
        builtIns.put(Double.class, wrap(ConfigMappers::toDouble));
        builtIns.put(Boolean.class, wrap(ConfigMappers::toBoolean));
        builtIns.put(Character.class, wrap(ConfigMappers::toChar));
        //java.lang
        builtIns.put(Class.class, wrap(ConfigMappers::toClass));
        //javax.math
        builtIns.put(BigDecimal.class, wrap(ConfigMappers::toBigDecimal));
        builtIns.put(BigInteger.class, wrap(ConfigMappers::toBigInteger));
        //java.time
        builtIns.put(Duration.class, wrap(ConfigMappers::toDuration));
        builtIns.put(Period.class, wrap(ConfigMappers::toPeriod));
        builtIns.put(LocalDate.class, wrap(ConfigMappers::toLocalDate));
        builtIns.put(LocalDateTime.class, wrap(ConfigMappers::toLocalDateTime));
        builtIns.put(LocalTime.class, wrap(ConfigMappers::toLocalTime));
        builtIns.put(ZonedDateTime.class, wrap(ConfigMappers::toZonedDateTime));
        builtIns.put(ZoneId.class, wrap(ConfigMappers::toZoneId));
        builtIns.put(ZoneOffset.class, wrap(ConfigMappers::toZoneOffset));
        builtIns.put(Instant.class, wrap(ConfigMappers::toInstant));
        builtIns.put(OffsetTime.class, wrap(ConfigMappers::toOffsetTime));
        builtIns.put(OffsetDateTime.class, wrap(ConfigMappers::toOffsetDateTime));
        builtIns.put(YearMonth.class, wrap(YearMonth::parse));
        //java.io
        builtIns.put(File.class, wrap(ConfigMappers::toFile));
        //java.nio
        builtIns.put(Path.class, wrap(ConfigMappers::toPath));
        builtIns.put(Charset.class, wrap(ConfigMappers::toCharset));
        //java.net
        builtIns.put(URI.class, wrap(ConfigMappers::toUri));
        builtIns.put(URL.class, wrap(ConfigMappers::toUrl));
        //java.util
        builtIns.put(Pattern.class, wrap(ConfigMappers::toPattern));
        builtIns.put(UUID.class, wrap(ConfigMappers::toUUID));
        builtIns.put(Map.class, wrapMapper(ConfigMappers::toMap));
        builtIns.put(Properties.class, wrapMapper(ConfigMappers::toProperties));

        // obsolete stuff
        //noinspection UseOfObsoleteDateTimeApi,deprecation
        builtIns.put(Date.class, wrap(ConfigMappers::toDate));
        //noinspection UseOfObsoleteDateTimeApi,deprecation
        builtIns.put(Calendar.class, wrap(ConfigMappers::toCalendar));
        //noinspection UseOfObsoleteDateTimeApi,deprecation
        builtIns.put(GregorianCalendar.class, wrap(ConfigMappers::toGregorianCalendar));
        //noinspection UseOfObsoleteDateTimeApi,deprecation
        builtIns.put(TimeZone.class, wrap(ConfigMappers::toTimeZone));
        //noinspection UseOfObsoleteDateTimeApi,deprecation
        builtIns.put(SimpleTimeZone.class, wrap(ConfigMappers::toSimpleTimeZone));

        return Collections.unmodifiableMap(builtIns);
    }

    static Map<Class<?>, Function<Config, ?>> builtInMappers() {
        return BUILT_IN_MAPPERS;
    }

    //
    // Public mapping utility functions
    //

    /**
     * Maps {@code stringValue} to {@code byte}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code byte}
     */
    public static Byte toByte(String stringValue) {
        return Byte.parseByte(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code short}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code short}
     */
    public static Short toShort(String stringValue) {
        return Short.parseShort(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code int}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code int}
     */
    public static Integer toInt(String stringValue) {
        return Integer.parseInt(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code long}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code long}
     */
    public static Long toLong(String stringValue) {
        return Long.parseLong(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code float}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code float}
     */
    public static Float toFloat(String stringValue) {
        return Float.parseFloat(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code double}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code double}
     */
    public static Double toDouble(String stringValue) {
        return Double.parseDouble(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code boolean}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code boolean}
     */
    public static Boolean toBoolean(String stringValue) {
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

    /**
     * Maps {@code stringValue} to {@code char}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code char}
     */
    public static Character toChar(String stringValue) {
        if (stringValue.length() != 1) {
            throw new IllegalArgumentException("Cannot convert to 'char'. The value must be just single character, "
                                                       + "but was '" + stringValue + "'.");
        }
        return stringValue.charAt(0);
    }

    /**
     * Maps {@code stringValue} to {@code Class<?>}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Class<?>}
     */
    public static Class<?> toClass(String stringValue) {
        try {
            return Class.forName(stringValue);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Maps {@code stringValue} to {@code UUID}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code UUID}
     */
    public static UUID toUUID(String stringValue) {
        return UUID.fromString(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code BigDecimal}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code BigDecimal}
     */
    public static BigDecimal toBigDecimal(String stringValue) {
        return new BigDecimal(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code BigInteger}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code BigInteger}
     */
    public static BigInteger toBigInteger(String stringValue) {
        return new BigInteger(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code File}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code File}
     */
    public static File toFile(String stringValue) {
        return new File(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Path}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Path}
     */
    public static Path toPath(String stringValue) {
        return Paths.get(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Charset}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Charset}
     */
    public static Charset toCharset(String stringValue) {
        return Charset.forName(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Pattern}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Pattern}
     */
    public static Pattern toPattern(String stringValue) {
        return Pattern.compile(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code URI}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code URI}
     */
    public static URI toUri(String stringValue) {
        return URI.create(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code URL}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code URL}
     */
    public static URL toUrl(String stringValue) {
        try {
            return new URL(stringValue);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Maps {@code stringValue} to {@code Date}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Date}
     * @see DateTimeFormatter#ISO_DATE_TIME
     * @deprecated Use one of the time API classes, such as {@link Instant} or {@link ZonedDateTime}
     */
    @SuppressWarnings({"UseOfObsoleteDateTimeApi", "DeprecatedIsStillUsed"})
    @Deprecated
    public static Date toDate(String stringValue) {
        try {
            return new Date(
                    Instant.from(buildDateTimeFormatter(stringValue).parse(stringValue)).toEpochMilli());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static DateTimeFormatter buildDateTimeFormatter(String stringValue) {
        /*
        A Java 8 bug causes DateTimeFormatter.withZone to override an explicit
        time zone in the parsed string, contrary to the documented behavior. So
        if the string includes a zone do NOT use withZone in building the formatter.
         */
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor accessor = formatter.parseUnresolved(stringValue, pp);
        if (!accessor.isSupported(ChronoField.OFFSET_SECONDS)) {
            formatter = formatter.withZone(ZoneId.of("UTC"));
        }
        return formatter;
    }

    /**
     * Maps {@code stringValue} to {@code Calendar}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Calendar}
     * @see DateTimeFormatter#ISO_DATE_TIME
     * @deprecated use new time API, such as {@link ZonedDateTime}
     */
    @SuppressWarnings({"UseOfObsoleteDateTimeApi", "DeprecatedIsStillUsed"})
    @Deprecated
    public static Calendar toCalendar(String stringValue) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(toDate(stringValue));
        return calendar;
    }

    /**
     * Maps {@code stringValue} to {@code GregorianCalendar}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code GregorianCalendar}
     * @see DateTimeFormatter#ISO_DATE_TIME
     * @deprecated use new time API, such as {@link ZonedDateTime}
     */
    @SuppressWarnings({"UseOfObsoleteDateTimeApi", "DeprecatedIsStillUsed"})
    @Deprecated
    public static GregorianCalendar toGregorianCalendar(String stringValue) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(toDate(stringValue));
        return calendar;
    }

    /**
     * Maps {@code stringValue} to {@code LocalDate}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code LocalDate}
     * @see LocalDate#parse(CharSequence)
     */
    public static LocalDate toLocalDate(String stringValue) {
        return LocalDate.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code LocalTime}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code LocalTime}
     * @see LocalTime#parse(CharSequence)
     */
    public static LocalTime toLocalTime(String stringValue) {
        return LocalTime.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code LocalDateTime}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code LocalDateTime}
     * @see LocalDateTime#parse(CharSequence)
     */
    public static LocalDateTime toLocalDateTime(String stringValue) {
        return LocalDateTime.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code ZonedDateTime}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code ZonedDateTime}
     * @see ZonedDateTime#parse(CharSequence)
     */
    public static ZonedDateTime toZonedDateTime(String stringValue) {
        return ZonedDateTime.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code ZoneId}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code ZoneId}
     * @see ZoneId#of(String)
     */
    public static ZoneId toZoneId(String stringValue) {
        return ZoneId.of(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code ZoneOffset}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code ZoneOffset}
     * @see ZoneOffset#of(String)
     */
    public static ZoneOffset toZoneOffset(String stringValue) {
        return ZoneOffset.of(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code TimeZone}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code TimeZone}
     * @see ZoneId#of(String)
     * @deprecated use new time API, such as {@link ZoneId}
     */
    @SuppressWarnings({"UseOfObsoleteDateTimeApi", "DeprecatedIsStillUsed"})
    @Deprecated
    public static TimeZone toTimeZone(String stringValue) {
        ZoneId zoneId = toZoneId(stringValue);
        return TimeZone.getTimeZone(zoneId);
    }

    /**
     * Maps {@code stringValue} to {@code SimpleTimeZone}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code SimpleTimeZone}
     * @see ZoneId#of(String)
     * @deprecated use new time API, such as {@link ZoneId}
     */
    @SuppressWarnings({"UseOfObsoleteDateTimeApi", "DeprecatedIsStillUsed"})
    @Deprecated
    public static SimpleTimeZone toSimpleTimeZone(String stringValue) {
        return new SimpleTimeZone(toTimeZone(stringValue).getRawOffset(), stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Instant}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Instant}
     * @see Instant#parse(CharSequence)
     */
    public static Instant toInstant(String stringValue) {
        return Instant.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code OffsetDateTime}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code OffsetDateTime}
     * @see OffsetDateTime#parse(CharSequence)
     */
    public static OffsetDateTime toOffsetDateTime(String stringValue) {
        return OffsetDateTime.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code OffsetTime}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code OffsetTime}
     * @see OffsetTime#parse(CharSequence)
     */
    public static OffsetTime toOffsetTime(String stringValue) {
        return OffsetTime.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Duration}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Duration}
     * @see Duration#parse(CharSequence)
     */
    public static Duration toDuration(String stringValue) {
        return Duration.parse(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Period}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Period}
     * @see Period#parse(CharSequence)
     */
    public static Period toPeriod(String stringValue) {
        return Period.parse(stringValue);
    }

    /**
     * Transform all leaf nodes (values) into Map instance.
     * <p>
     * Fully qualified key of config node is used as a key in returned Map.
     * {@link Config#detach() Detach} config node before transforming to Map in case you want to cut
     * current Config node key prefix.
     * <p>
     * Let's say we work with following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * Map {@code app1} contains two keys: {@code app.name}, {@code app.page-size}.
     * <pre>{@code
     * Map<String, String> app1 = ConfigMappers.toMap(config.get("app"));
     * }</pre>
     * {@link Config#detach() Detaching} {@code app} config node returns new Config instance with "reset" local root.
     * <pre>{@code
     * Map<String, String> app2 = ConfigMappers.toMap(config.get("app").detach());
     * }</pre>
     * Map {@code app2} contains two keys without {@code app} prefix: {@code name}, {@code page-size}.
     *
     * @param config config node used to transform into Properties
     * @return new Map instance that contains all config leaf node values
     * @see Config#detach()
     */
    public static Map<String, String> toMap(Config config) {
        if (config.isLeaf()) {
            return new StringMap(config.key().toString(), config.asString().get());
        } else {
            return new StringMap(config.traverse()
                                         .filter(Config::isLeaf)
                                         .map(node -> new AbstractMap.SimpleEntry<>(node.key().toString(), node.asString().get()))
                                         .collect(Collectors.toSet()));
        }
    }

    /**
     * Transform all leaf nodes (values) into Properties instance.
     * <p>
     * Fully qualified key of config node is used as a key in returned Properties.
     * {@link Config#detach() Detach} config node before transforming to Properties in case you want to cut
     * current Config node key prefix.
     * <p>
     * Let's say we work with following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * Properties {@code app1} contains two keys: {@code app.name}, {@code app.page-size}.
     * <pre>
     * Properties app1 = ConfigMappers.toProperties(config.get("app"));
     * </pre>
     * {@link Config#detach() Detaching} {@code app} config node returns new Config instance with "reset" local root.
     * <pre>
     * Properties app2 = ConfigMappers.toProperties(config.get("app").detach());
     * </pre>
     * Properties {@code app2} contains two keys without {@code app} prefix: {@code name}, {@code page-size}.
     *
     * @param config config node used to transform into Properties
     * @return Properties instance that contains all config leaf node values.
     * @see Config#detach()
     */
    public static Properties toProperties(Config config) {
        Properties properties = new Properties();
        toMap(config).forEach(properties::setProperty);
        return properties;
    }

    /**
     * Utility method wrapping an arbitrary mapper and ensuring proper exceptions are produced if needed.
     *
     * @param mapper mapping function. Function throws {@link ConfigMappingException} in case the value cannot be mapped.
     * @param <T>    mapped Java type.
     * @return mapped value.
     */
    private static <T> Function<Config, T> wrapMapper(Function<Config, T> mapper) {
        return (node) -> {
            try {
                return mapper.apply(node);
            } catch (MissingValueException | ConfigMappingException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new ConfigMappingException(
                        node.key(), "Invocation of mapper '" + mapper + "' has failed with an exception.", ex);
            }
        };
    }

    /**
     * Wrap a simple String value mapping function into a contextual mapper.
     *
     * @param mapper arbitrary String configuration value to a Java type mapping function.
     * @param <T>    mapped Java type.
     * @return contextual mapper wrapping the simple String value mapping function.
     * @throws MissingValueException  in case the configuration node does not represent an existing configuration node.
     * @throws ConfigMappingException in case the mapper fails to map the existing configuration value
     *                                to an instance of a given Java type.
     */
    static <T> Function<Config, T> wrap(Function<String, T> mapper) {
        return (node) -> nodeValue(node)
                .map(value -> safeMap(node.key(), value, mapper))
                .orElseThrow(MissingValueException.createSupplier(node.key()));
    }

    private static Optional<String> nodeValue(Config node) {
        if (node instanceof AbstractConfigImpl) {
            return ((AbstractConfigImpl) node).value();
        }
        return node.asString().asOptional();
    }

    /**
     * Utility method wrapping an arbitrary mapping function and ensuring proper exceptions are produced if needed.
     *
     * @param key    configuration key of the mapped value.
     * @param value  String representation of the mapped value.
     * @param mapper mapping function. Function throws {@link ConfigMappingException} in case the value cannot be mapped.
     * @param <T>    mapped Java type.
     * @return mapped value.
     * @throws ConfigMappingException in case the mapper fails to map the existing configuration value
     *                                to an instance of a given Java type.
     */
    private static <T> T safeMap(Config.Key key, String value, Function<String, T> mapper) throws ConfigMappingException {
        try {
            return mapper.apply(value);
        } catch (ConfigMappingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ConfigMappingException(
                    key, value, "Invocation of mapper '" + mapper + "' has failed with an exception.", ex);
        }
    }

    /**
     * Concrete implementation of {@code Map<String, String>} support custom plugin of Map Mapper.
     */
    static class StringMap extends AbstractMap<String, String> implements Map<String, String> {
        private final Set<Entry<String, String>> entrySet;

        StringMap(Set<Entry<String, String>> entrySet) {
            this.entrySet = entrySet;
        }

        StringMap(String key, String value) {
            this(Set.of(Map.entry(key, value)));
        }

        StringMap(Map<?, ?> unknownMap) {
            this(wrap(unknownMap.entrySet()));
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return entrySet;
        }

        private static Set<Entry<String, String>> wrap(Set<? extends Entry<?, ?>> unknownEntrySet) {
            return unknownEntrySet.stream()
                    .map(entry -> new AbstractMap.SimpleEntry<>(Objects.toString(entry.getKey()),
                                                                Objects.toString(entry.getValue())))
                    .collect(Collectors.toSet());
        }
    }
}
