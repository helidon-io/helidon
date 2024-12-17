/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
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

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Java {@link java.util.ServiceLoader} service to get database types mappers.
 */
public class DbMapperProviderImpl implements MapperProvider  {

    private static final BigInteger BIG_INTEGER_ZERO = BigInteger.valueOf(0L);
    private static final BigDecimal BIG_DECIMAL_ZERO = BigDecimal.valueOf(0L);
    // Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();
    private static final Map<Class<?>, Mapper<Number, ?>> NUMBER_MAPPERS = initNumberMappers();
    private static final Map<Class<?>, Mapper<Boolean, ?>> BOOLEAN_MAPPERS = initBooleanMappers();

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        if (Boolean.class.isAssignableFrom(sourceClass)) {
            Mapper<Boolean, ?> mapper = BOOLEAN_MAPPERS.get(targetClass);
            if (mapper != null) {
                return new ProviderResponse(Support.SUPPORTED, mapper);
            }
        } else if (Number.class.isAssignableFrom(sourceClass)) {
            Mapper<Number, ?> mapper = NUMBER_MAPPERS.get(targetClass);
            if (mapper != null) {
                return new ProviderResponse(Support.SUPPORTED, mapper);
            }
        }
        Map<Class<?>, Mapper<?, ?>> targetMap = MAPPERS.get(sourceClass);
        if (targetMap == null) {
            return ProviderResponse.unsupported();
        }
        Mapper<?, ?> mapper = targetMap.get(targetClass);
        return mapper == null ? ProviderResponse.unsupported() : new ProviderResponse(Support.SUPPORTED, mapper);
    }

    // MAPPERS initializer
    private static Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> initMappers() {
        // All mappers index
        Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> mappers = new HashMap<>(3);
        // * Mappers for java.sql.Timestamp source
        Map<Class<?>, Mapper<Timestamp, Object>> sqlTimestampMap = new HashMap<>(4);
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
                DbMapperProviderImpl::sqlTimestampToGregorianCalendar);
        //   - Mapper for java.sql.Timestamp to java.util.GregorianCalendar
        sqlTimestampMap.put(
                GregorianCalendar.class,
                DbMapperProviderImpl::sqlTimestampToGregorianCalendar);
        mappers.put(
                Timestamp.class,
                Collections.unmodifiableMap(sqlTimestampMap));
        // * Mappers for java.sql.Date source
        //   - Mapper for java.sql.Date to java.util.Date
        mappers.put(
                java.sql.Date.class,
                Map.of(Date.class, source -> source,
                       //   - Mapper for java.sql.Date to java.time.LocalDate
                       LocalDate.class, (java.sql.Date source) -> source != null
                                ? source.toLocalDate()
                                : null));
        // * Mappers for java.sql.Time source
        Map<Class<?>, Mapper<Time, Object>> sqlTimeMap = new HashMap<>(2);
        //   - Mapper for java.sql.Time to java.util.Date
        sqlTimeMap.put(
                Date.class,
                source -> source);
        //   - Mapper for java.sql.Time to java.time.LocalTime
        sqlTimeMap.put(
                LocalTime.class,
                (Time source) -> source != null
                        ? source.toLocalTime()
                        : null);
        mappers.put(
                Time.class,
                Collections.unmodifiableMap(sqlTimeMap));
        return Collections.unmodifiableMap(mappers);
    }

    // NUMBER_MAPPERS initializer
    private static Map<Class<?>, Mapper<Number, ?>> initNumberMappers() {
        Map<Class<?>, Mapper<Number, ?>> numberMappers = new HashMap<>();
        // * Mapper for Number to byte value
        numberMappers.put(byte.class, Number::byteValue);
        numberMappers.put(Byte.class, DbMapperProviderImpl::numberToByte);
        // * Mapper for Number to short value
        numberMappers.put(short.class, Number::shortValue);
        numberMappers.put(Short.class, DbMapperProviderImpl::numberToShort);
        // * Mapper for Number to int value
        numberMappers.put(int.class, Number::intValue);
        numberMappers.put(Integer.class, DbMapperProviderImpl::numberToInt);
        // * Mapper for Number to long value
        numberMappers.put(long.class, Number::longValue);
        numberMappers.put(Long.class, DbMapperProviderImpl::numberToLong);
        // * Mapper for Number to float value
        numberMappers.put(float.class, Number::floatValue);
        numberMappers.put(Float.class, DbMapperProviderImpl::numberToFloat);
        // * Mapper for Number to double value
        numberMappers.put(double.class, Number::doubleValue);
        numberMappers.put(Double.class, DbMapperProviderImpl::numberToDouble);
        // * Mapper for Number to boolean value
        numberMappers.put(boolean.class, DbMapperProviderImpl::numberToBoolean);
        numberMappers.put(Boolean.class, DbMapperProviderImpl::numberToBoxedBoolean);
        return Collections.unmodifiableMap(numberMappers);
    }

    // BOOLEAN_MAPPERS initializer
    private static Map<Class<?>, Mapper<Boolean, ?>> initBooleanMappers() {
        Map<Class<?>, Mapper<Boolean, ?>> booleanMappers = new HashMap<>();
        // * Mapper for Boolean to boolean value
        booleanMappers.put(boolean.class, Boolean::booleanValue);
        // * Mapper for Boolean to byte value
        booleanMappers.put(byte.class, DbMapperProviderImpl::booleanToByte);
        booleanMappers.put(Byte.class, DbMapperProviderImpl::booleanToBoxedByte);
        // * Mapper for Boolean to short value
        booleanMappers.put(short.class, DbMapperProviderImpl::booleanToShort);
        booleanMappers.put(Short.class, DbMapperProviderImpl::booleanToBoxedShort);
        // * Mapper for Boolean to int value
        booleanMappers.put(int.class, DbMapperProviderImpl::booleanToInt);
        booleanMappers.put(Integer.class, DbMapperProviderImpl::booleanToBoxedInt);
        // * Mapper for Boolean to long value
        booleanMappers.put(long.class, DbMapperProviderImpl::booleanToLong);
        booleanMappers.put(Long.class, DbMapperProviderImpl::booleanToBoxedLong);
        // * Mapper for Boolean to float value
        booleanMappers.put(float.class, DbMapperProviderImpl::booleanToFloat);
        booleanMappers.put(Float.class, DbMapperProviderImpl::booleanToBoxedFloat);
        // * Mapper for Boolean to double value
        booleanMappers.put(double.class, DbMapperProviderImpl::booleanToDouble);
        booleanMappers.put(Double.class, DbMapperProviderImpl::booleanToBoxedDouble);
        return Collections.unmodifiableMap(booleanMappers);
    }

    // Maps {@link java.sql.Timestamp} to {@link java.util.Calendar} with zone set to UTC.
    private static GregorianCalendar sqlTimestampToGregorianCalendar(Timestamp source) {
        return source != null
                ? GregorianCalendar.from(ZonedDateTime.ofInstant(source.toInstant(), ZoneOffset.UTC))
                : null;
    }

    // Maps {@link Number} to Byte value.
    private static Byte numberToByte(Number source) {
        return source != null ? source.byteValue() : null;
    }

    // Maps {@link Number} to Short value.
    private static Short numberToShort(Number source) {
        return source != null ? source.shortValue() : null;
    }

    // Maps {@link Number} to Integer value.
    private static Integer numberToInt(Number source) {
        return source != null ? source.intValue() : null;
    }

    // Maps {@link Number} to Long value.
    private static Long numberToLong(Number source) {
        return source != null ? source.longValue() : null;
    }

    // Maps {@link Number} to Float value.
    private static Float numberToFloat(Number source) {
        return source != null ? source.floatValue() : null;
    }

    // Maps {@link Number} to Double value.
    private static Double numberToDouble(Number source) {
        return source != null ? source.doubleValue() : null;
    }

    // Maps {@link Number} to Boolean value.
    private static Boolean numberToBoxedBoolean(Number source) {
        return source != null && numberToBooleanImpl(source);
    }

    // Maps {@link Number} to boolean value.
    // Value of {@code null} will throw {@link NullPointerException}.
    private static boolean numberToBoolean(Number source) {
        return numberToBooleanImpl(source);
    }

    // Maps {@link Boolean} to byte value.
    private static byte booleanToByte(Boolean source) {
        return source ? (byte) 1 : (byte) 0;
    }

    // Maps {@link Boolean} to Byte value.
    private static Byte booleanToBoxedByte(Boolean source) {
        return source == null ? null : booleanToByte(source);
    }

    // Maps {@link Boolean} to short value.
    private static short booleanToShort(Boolean source) {
        return source ? (short) 1 : (short) 0;
    }

    // Maps {@link Boolean} to Short value.
    private static Short booleanToBoxedShort(Boolean source) {
        return source == null ? null : booleanToShort(source);
    }

    // Maps {@link Boolean} to int value.
    private static int booleanToInt(Boolean source) {
        return source ? 1 : 0;
    }

    // Maps {@link Boolean} to Integer value.
    private static Integer booleanToBoxedInt(Boolean source) {
        return source == null ? null : booleanToInt(source);
    }

    // Maps {@link Boolean} to long value.
    private static long booleanToLong(Boolean source) {
        return source ? 1L : 0L;
    }

    // Maps {@link Boolean} to Long value.
    private static Long booleanToBoxedLong(Boolean source) {
        return source == null ? null : booleanToLong(source);
    }

    // Maps {@link Boolean} to float value.
    private static float booleanToFloat(Boolean source) {
        return source ? 1f : 0f;
    }

    // Maps {@link Boolean} to Float value.
    private static Float booleanToBoxedFloat(Boolean source) {
        return source == null ? null : booleanToFloat(source);
    }

    // Maps {@link Boolean} to double value.
    private static double booleanToDouble(Boolean source) {
        return source ? 1d : 0d;
    }

    // Maps {@link Boolean} to Double value.
    private static Double booleanToBoxedDouble(Boolean source) {
        return source == null ? null : booleanToDouble(source);
    }

    // {@link Number} to boolean conversion implementation
    private static boolean numberToBooleanImpl(Number source) {
        return switch (source) {
            case Byte b -> b > 0;
            case Short s -> s > 0;
            case Integer i -> i > 0;
            case Long l -> l > 0L;
            case Float f -> f > 0f;
            case Double d -> d > 0d;
            case BigInteger bi -> bi.compareTo(BIG_INTEGER_ZERO) > 0;
            case BigDecimal bd -> bd.compareTo(BIG_DECIMAL_ZERO) > 0;
            // Try both fixed and floating point
            default -> source.longValue() > 0L || source.doubleValue() > 0d;
        };
    }

}
