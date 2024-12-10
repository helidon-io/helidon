/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import java.util.ServiceLoader;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Java {@link ServiceLoader} service to get database types mappers.
 */
public class DbMapperProviderImpl implements MapperProvider  {

    /**
     * Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
     */
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();

    @SuppressWarnings("Java9CollectionFactory")
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
        mappers.put(
                java.sql.Time.class,
                Collections.unmodifiableMap(sqlTimeMap));
        return Collections.unmodifiableMap(mappers);
    }

    public DbMapperProviderImpl() {
    }

    @Override
    public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
        Map<Class<?>, Mapper<?, ?>> targetMap = MAPPERS.get(sourceClass);
        if (targetMap == null) {
            return ProviderResponse.unsupported();
        }
        Mapper<?, ?> mapper = targetMap.get(targetClass);
        return mapper == null ? ProviderResponse.unsupported() : new ProviderResponse(Support.SUPPORTED, mapper);
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
