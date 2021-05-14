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

package io.helidon.dbclient.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Java Service loader service to get database types mappers.
 */
public class DbClientMapperProvider implements MapperProvider {

    /**
     * Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
     */
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();

    private static Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> initMappers() {
        Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> mappers = new HashMap<>(6);
        mappers.put(Timestamp.class, timestampMappers());
        mappers.put(java.sql.Date.class, sqlDateMappers());
        mappers.put(java.sql.Time.class, sqlTimeMappers());
        mappers.put(BigInteger.class, bigIntegerMappers());
        mappers.put(BigDecimal.class, bigDecimalMappers());
        mappers.put(Long.class, longMappers());

        return Collections.unmodifiableMap(mappers);
    }

    // Mappers for java.sql.Timestamp source
    private static Map<Class<?>, Mapper<?, ?>> timestampMappers() {
        Map<Class<?>, Mapper<Timestamp, Object>> sqlTimestampMap = new HashMap<>(5);
        //   - Mapper for java.sql.Timestamp to java.util.Date
        sqlTimestampMap.put(
                Date.class,
                source -> source);
        //   - Mapper for java.sql.Timestamp to java.time.LocalDateTime
        sqlTimestampMap.put(
                LocalDateTime.class,
                (Timestamp source) -> source != null
                        ? source.toLocalDateTime()
                        : null);
        //   - Mapper for java.sql.Timestamp to java.time.ZonedDateTime
        sqlTimestampMap.put(
                ZonedDateTime.class,
                (Timestamp source) -> source != null
                        ? ZonedDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC)
                        : null);
        //   - Mapper for java.sql.Timestamp to java.util.Calendar
        sqlTimestampMap.put(
                Calendar.class,
                DbClientMapperProvider::sqlTimestampToGregorianCalendar);
        //   - Mapper for java.sql.Timestamp to java.util.GregorianCalendar
        sqlTimestampMap.put(
                GregorianCalendar.class,
                DbClientMapperProvider::sqlTimestampToGregorianCalendar);
        return Collections.unmodifiableMap(sqlTimestampMap);
    }

    // Mappers for java.sql.Date source
    private static Map<Class<?>, Mapper<?, ?>> sqlDateMappers() {
        Map<Class<?>, Mapper<java.sql.Date, Object>> sqlDateMap = new HashMap<>(2);
        //   - Mapper for java.sql.Date to java.util.Date
        sqlDateMap.put(
                Date.class,
                source -> source);
        //   - Mapper for java.sql.Date to java.time.LocalDate
        sqlDateMap.put(
                LocalDate.class,
                (java.sql.Date source) -> source != null
                        ? source.toLocalDate()
                        : null);
        return Collections.unmodifiableMap(sqlDateMap);
    }

    // Mappers for java.sql.Time source
    private static Map<Class<?>, Mapper<?, ?>> sqlTimeMappers() {
        Map<Class<?>, Mapper<java.sql.Time, Object>> sqlTimeMap = new HashMap<>(2);
        //   - Mapper for java.sql.Time to java.util.Date
        sqlTimeMap.put(
                Date.class,
                source -> source);
        //   - Mapper for java.sql.Time to java.time.LocalTime
        sqlTimeMap.put(
                LocalTime.class,
                (java.sql.Time source) -> source != null
                        ? source.toLocalTime()
                        : null);
        return Collections.unmodifiableMap(sqlTimeMap);
    }

    // Mappers for java.math.BigInteger source
    private static Map<Class<?>, Mapper<?, ?>> bigIntegerMappers() {
        Map<Class<?>, Mapper<BigInteger, Object>> bigIntegerMap = new HashMap<>(4);
        //   - Mapper for BigInteger to Byte
        bigIntegerMap.put(
                Byte.class,
                source -> source.byteValueExact());
        //   - Mapper for BigInteger to Short
        bigIntegerMap.put(
                Short.class,
                source -> source.shortValueExact());
        //   - Mapper for BigInteger to Integer
        bigIntegerMap.put(
                Integer.class,
                source -> source.intValueExact());
        //   - Mapper for BigInteger to Long
        bigIntegerMap.put(
                Long.class,
                source -> source.longValueExact());
        return Collections.unmodifiableMap(bigIntegerMap);
    }

    // Mappers for java.math.BigDecimal source
    private static Map<Class<?>, Mapper<?, ?>> bigDecimalMappers() {
        Map<Class<?>, Mapper<BigDecimal, Object>> bigDecimalMap = new HashMap<>(4);
        //   - Mapper for BigDecimal to Byte
        bigDecimalMap.put(
                Byte.class,
                source -> source.byteValueExact());
        //   - Mapper for BigDecimal to Short
        bigDecimalMap.put(
                Short.class,
                source -> source.shortValueExact());
        //   - Mapper for BigDecimal to Integer
        bigDecimalMap.put(
                Integer.class,
                source -> source.intValueExact());
        //   - Mapper for BigDecimal to Long
        bigDecimalMap.put(
                Long.class,
                source -> source.longValueExact());
        return Collections.unmodifiableMap(bigDecimalMap);
    }

    // Mappers for Long source
    private static Map<Class<?>, Mapper<?, ?>> longMappers() {
        Map<Class<?>, Mapper<Long, Object>> longMap = new HashMap<>(3);
        //   - Mapper for Long to Byte
        longMap.put(
                Byte.class,
                source -> {
                    if (source < Byte.MIN_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too low for Byte", source));
                    }
                    if (source > Byte.MAX_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too high for Byte", source));
                    }
                    return source.byteValue();
                });
        //   - Mapper for Long to Short
        longMap.put(
                Short.class,
                source -> {
                    if (source < Short.MIN_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too low for Short", source));
                    }
                    if (source > Short.MAX_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too high for Short", source));
                    }
                    return source.shortValue();
                });
        //   - Mapper for Long to Integer
        longMap.put(
                Integer.class,
                source -> {
                    if (source < Integer.MIN_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too low for Integer", source));
                    }
                    if (source > Integer.MAX_VALUE) {
                        throw new ArithmeticException(String.format("Value of %d is too high for Integer", source));
                    }
                    return source.intValue();
                });
        return Collections.unmodifiableMap(longMap);
    }

    @Override
    public <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass) {
        Map<Class<?>, Mapper<?, ?>> targetMap = MAPPERS.get(sourceClass);
        if (targetMap == null) {
            return Optional.empty();
        }
        Mapper<?, ?> mapper = targetMap.get(targetClass);
        return mapper == null ? Optional.empty() : Optional.of(mapper);
    }

    /**
     * Maps {@link java.sql.Timestamp} to {@link java.util.Calendar} with zone set to UTC.
     */
    private static GregorianCalendar sqlTimestampToGregorianCalendar(Timestamp source) {
        return source != null
                ? GregorianCalendar.from(ZonedDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC))
                : null;
    }

}
