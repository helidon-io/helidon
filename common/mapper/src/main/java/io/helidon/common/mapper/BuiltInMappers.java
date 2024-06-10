/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

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
        addStringMapper(mappers, boolean.class, BuiltInMappers::asBoolean);
        addStringMapper(mappers, Byte.class, BuiltInMappers::asByte);
        addStringMapper(mappers, byte.class, BuiltInMappers::asByte);
        addStringMapper(mappers, Short.class, BuiltInMappers::asShort);
        addStringMapper(mappers, short.class, BuiltInMappers::asShort);
        addStringMapper(mappers, Integer.class, BuiltInMappers::asInt);
        addStringMapper(mappers, int.class, BuiltInMappers::asInt);
        addStringMapper(mappers, Long.class, BuiltInMappers::asLong);
        addStringMapper(mappers, long.class, BuiltInMappers::asLong);
        addStringMapper(mappers, Float.class, BuiltInMappers::asFloat);
        addStringMapper(mappers, float.class, BuiltInMappers::asFloat);
        addStringMapper(mappers, Double.class, BuiltInMappers::asDouble);
        addStringMapper(mappers, double.class, BuiltInMappers::asDouble);
        addStringMapper(mappers, Character.class, BuiltInMappers::asChar);
        addStringMapper(mappers, char.class, BuiltInMappers::asChar);
        addStringMapper(mappers, Class.class, BuiltInMappers::asClass);
        //javax.math
        addStringMapper(mappers, BigDecimal.class, BigDecimal::new);
        addStringMapper(mappers, BigInteger.class, BigInteger::new);
        //java.io
        addStringMapper(mappers, File.class, File::new);
        //java.nio
        addStringMapper(mappers, Path.class, Paths::get);
        addStringMapper(mappers, Charset.class, Charset::forName);
        //java.net
        addStringMapper(mappers, URI.class, URI::create);
        addStringMapper(mappers, URL.class, BuiltInMappers::asUrl);
        //java.util
        addStringMapper(mappers, Pattern.class, Pattern::compile);
        addStringMapper(mappers, UUID.class, UUID::fromString);
        //time/date operations
        addStringMapper(mappers, Duration.class, Duration::parse);
        addStringMapper(mappers, Period.class, Period::parse);
        addStringMapper(mappers, ZoneId.class, ZoneId::of);
        addStringMapper(mappers, ZoneOffset.class, ZoneOffset::of);
        // time/date formatted operations
        addStringMapper(mappers, LocalDate.class, LocalDate::parse);
        addStringMapper(mappers, LocalDateTime.class, LocalDateTime::parse);
        addStringMapper(mappers, LocalTime.class, LocalTime::parse);
        addStringMapper(mappers, ZonedDateTime.class, ZonedDateTime::parse);
        addStringMapper(mappers, Instant.class, Instant::parse);
        addStringMapper(mappers, OffsetTime.class, OffsetTime::parse);
        addStringMapper(mappers, OffsetDateTime.class, OffsetDateTime::parse);
        addStringMapper(mappers, YearMonth.class, YearMonth::parse);

        MAPPERS = Map.copyOf(mappers);
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
        String lower = stringValue.toLowerCase();
        // according to microprofile config specification (section Built-in Converters)
        return switch (lower) {
            case "true", "1", "yes", "y", "on" -> true;
            default -> false;
        };
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
     * Maps {@code stringValue} to {@code URL}.
     *
     * @param stringValue source value as a {@code String}
     * @return mapped {@code stringValue} to {@code URL}
     */
    private static URL asUrl(String stringValue) {
        try {
            return URI.create(stringValue).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        if (!qualifier.isEmpty()) {
            return ProviderResponse.unsupported();
        }
        Mapper<?, ?> mapper = MAPPERS.get(new ClassPair(sourceClass, targetClass));
        if (mapper != null) {
            return new ProviderResponse(Support.SUPPORTED, mapper);
        }
        if (targetClass.equals(String.class)) {
            return new ProviderResponse(Support.COMPATIBLE, String::valueOf);
        }
        return ProviderResponse.unsupported();
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
