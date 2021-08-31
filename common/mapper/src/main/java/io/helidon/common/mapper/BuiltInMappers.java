/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.helidon.common.context.Contexts;
import io.helidon.common.mapper.spi.MapperProvider;

/*
 * Built in mapper that are "clean" to use across all components.
 * This does not contain many of date/time mapping, as that is context sensitive (e.g. you need a different
 * format depending on the component used).
 */
class BuiltInMappers implements MapperProvider {
    private static final Map<ClassPair, Mapper<?, ?>> MAPPERS;

    static {
        Map<ClassPair, Mapper<?, ?>> mappers = new HashMap<>();
        // basic types
        addStringMapper(mappers, Boolean.class, BuiltInMappers::asBoolean);
        addStringMapper(mappers, Byte.class, BuiltInMappers::asByte);
        addStringMapper(mappers, Short.class, BuiltInMappers::asShort);
        addStringMapper(mappers, Integer.class, BuiltInMappers::asInt);
        addStringMapper(mappers, Long.class, BuiltInMappers::asLong);
        addStringMapper(mappers, Float.class, BuiltInMappers::asFloat);
        addStringMapper(mappers, Double.class, BuiltInMappers::asDouble);
        addStringMapper(mappers, Character.class, BuiltInMappers::asChar);
        addStringMapper(mappers, Class.class, BuiltInMappers::asClass);
        //javax.math
        addStringMapper(mappers, BigDecimal.class, BuiltInMappers::asBigDecimal);
        addStringMapper(mappers, BigInteger.class, BuiltInMappers::asBigInteger);
        //java.io
        addStringMapper(mappers, File.class, BuiltInMappers::asFile);
        //java.nio
        addStringMapper(mappers, Path.class, BuiltInMappers::asPath);
        addStringMapper(mappers, Charset.class, BuiltInMappers::asCharset);
        //java.net
        addStringMapper(mappers, URI.class, BuiltInMappers::asUri);
        addStringMapper(mappers, URL.class, BuiltInMappers::asUrl);
        //java.util
        addStringMapper(mappers, Pattern.class, BuiltInMappers::asPattern);
        addStringMapper(mappers, UUID.class, BuiltInMappers::asUUID);
        //time/date operations
        addStringMapper(mappers, Duration.class, BuiltInMappers::asDuration);
        addStringMapper(mappers, Period.class, BuiltInMappers::asPeriod);
        addStringMapper(mappers, ZoneId.class, BuiltInMappers::asZoneId);
        addStringMapper(mappers, ZoneOffset.class, BuiltInMappers::asZoneOffset);
        // time/date formatted operations
        addStringMapper(mappers, LocalDate.class, BuiltInMappers::asLocalDate);
        addStringMapper(mappers, LocalDateTime.class, BuiltInMappers::asLocalDateTime);
        addStringMapper(mappers, LocalTime.class, BuiltInMappers::asLocalTime);
        addStringMapper(mappers, ZonedDateTime.class, BuiltInMappers::asZonedDateTime);
        addStringMapper(mappers, Instant.class, BuiltInMappers::asInstant);
        addStringMapper(mappers, OffsetTime.class, BuiltInMappers::asOffsetTime);
        addStringMapper(mappers, OffsetDateTime.class, BuiltInMappers::asOffsetDateTime);
        addStringMapper(mappers, YearMonth.class, BuiltInMappers::asYearMonth);

        MAPPERS = Map.copyOf(mappers);
    }

    private static YearMonth asYearMonth(String stringValue) {
        return parseCalendarType(stringValue,
                                 YearMonth::parse,
                                 YearMonth::parse);
    }

    private static OffsetDateTime asOffsetDateTime(String stringValue) {
        return parseCalendarType(stringValue,
                                 OffsetDateTime::parse,
                                 OffsetDateTime::parse);
    }

    private static OffsetTime asOffsetTime(String stringValue) {
        return parseCalendarType(stringValue,
                                 OffsetTime::parse,
                                 OffsetTime::parse);
    }

    private static Instant asInstant(String stringValue) {
        return Contexts.context()
                .flatMap(it -> it.get(MapperManager.FORMAT_CLASSIFIER, DateTimeFormatter.class)
                        .or(() -> it.get(MapperManager.FORMAT_CLASSIFIER, String.class)
                                .map(DateTimeFormatter::ofPattern)))
                .map(it -> it.parse(stringValue, Instant::from))
                .orElseGet(() -> Instant.parse(stringValue));
    }

    private static ZoneOffset asZoneOffset(String stringValue) {
        return ZoneOffset.of(stringValue);
    }

    private static ZoneId asZoneId(String stringValue) {
        return ZoneId.of(stringValue);
    }

    private static ZonedDateTime asZonedDateTime(String stringValue) {
        return parseCalendarType(stringValue,
                                 ZonedDateTime::parse,
                                 ZonedDateTime::parse);
    }

    private static LocalTime asLocalTime(String stringValue) {
        return parseCalendarType(stringValue,
                                 LocalTime::parse,
                                 LocalTime::parse);
    }

    private static LocalDateTime asLocalDateTime(String stringValue) {
        return parseCalendarType(stringValue,
                                 LocalDateTime::parse,
                                 LocalDateTime::parse);
    }

    private static LocalDate asLocalDate(String stringValue) {
        return parseCalendarType(stringValue,
                                 LocalDate::parse,
                                 LocalDate::parse);
    }

    private static <T> T parseCalendarType(String stringValue,
                                           Function<String, T> baseParser,
                                           BiFunction<String, DateTimeFormatter, T> formattedParser) {

        return Contexts.context()
                .flatMap(it -> it.get(MapperManager.FORMAT_CLASSIFIER, DateTimeFormatter.class)
                        .or(() -> it.get(MapperManager.FORMAT_CLASSIFIER, String.class)
                                .map(DateTimeFormatter::ofPattern)))
                .map(it -> formattedParser.apply(stringValue, it))
                .orElseGet(() -> baseParser.apply(stringValue));
    }

    private static <T> void addStringMapper(Map<ClassPair, Mapper<?, ?>> mappers,
                                            Class<T> targetType,
                                            Function<String, T> mapperFx) {
        Mapper<String, T> mapper = mapperFx::apply;
        mappers.put(new ClassPair(String.class, targetType), mapper);
    }

    /**
     * Maps {@code stringValue} to {@code byte}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code byte}
     */
    private static Byte asByte(String stringValue) {
        return Byte.parseByte(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code short}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code short}
     */
    private static Short asShort(String stringValue) {
        return Short.parseShort(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code int}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code int}
     */
    private static Integer asInt(String stringValue) {
        return Integer.parseInt(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code long}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code long}
     */
    private static Long asLong(String stringValue) {
        return Long.parseLong(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code float}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code float}
     */
    private static Float asFloat(String stringValue) {
        return Float.parseFloat(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code double}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code double}
     */
    private static Double asDouble(String stringValue) {
        return Double.parseDouble(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code boolean}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code boolean}
     */
    private static Boolean asBoolean(String stringValue) {
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
    private static Character asChar(String stringValue) {
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
    private static Class<?> asClass(String stringValue) {
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
    private static UUID asUUID(String stringValue) {
        return UUID.fromString(stringValue);
    }

    private static Duration asDuration(String durationString) {
        return Duration.parse(durationString);
    }

    private static Period asPeriod(String periodString) {
        return Period.parse(periodString);
    }

    /**
     * Maps {@code stringValue} to {@code BigDecimal}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code BigDecimal}
     */
    private static BigDecimal asBigDecimal(String stringValue) {
        return new BigDecimal(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code BigInteger}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code BigInteger}
     */
    private static BigInteger asBigInteger(String stringValue) {
        return new BigInteger(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code File}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code File}
     */
    private static File asFile(String stringValue) {
        return new File(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Path}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Path}
     */
    private static Path asPath(String stringValue) {
        return Paths.get(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Charset}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Charset}
     */
    private static Charset asCharset(String stringValue) {
        return Charset.forName(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code Pattern}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code Pattern}
     */
    private static Pattern asPattern(String stringValue) {
        return Pattern.compile(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code URI}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code URI}
     */
    private static URI asUri(String stringValue) {
        return URI.create(stringValue);
    }

    /**
     * Maps {@code stringValue} to {@code URL}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code URL}
     */
    private static URL asUrl(String stringValue) {
        try {
            return new URL(stringValue);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static String asString(Object object) {
        return String.valueOf(object);
    }

    @Override
    public <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass) {
        Mapper<?, ?> mapper = MAPPERS.get(new ClassPair(sourceClass, targetClass));
        if (mapper != null) {
            return Optional.of(mapper);
        }
        if (targetClass.equals(String.class)) {
            return Optional.of(BuiltInMappers::asString);
        }
        return Optional.empty();
    }

    static final class ClassPair {
        private final Class<?> source;
        private final Class<?> target;

        ClassPair(Class<?> source, Class<?> target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassPair classPair = (ClassPair) o;
            return source.equals(classPair.source) && target.equals(classPair.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }
    }
}
