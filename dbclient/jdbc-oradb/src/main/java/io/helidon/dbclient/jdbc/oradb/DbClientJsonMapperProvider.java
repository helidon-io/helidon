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

package io.helidon.dbclient.jdbc.oradb;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.dbclient.DbClientException;

import oracle.sql.json.OracleJsonDate;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonTimestamp;
import oracle.sql.json.OracleJsonTimestampTZ;

/**
 * Java Service loader service to get JSON database types mappers.
 */
public class DbClientJsonMapperProvider implements MapperProvider {

    private static final Logger LOGGER = Logger.getLogger(DbClientJsonMapperProvider.class.getName());

    //Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();

    // initialize mappers instances
    private static Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> initMappers() {

        final OracleJsonFactory factory = new OracleJsonFactory();
        Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> mappers = new HashMap<>(2);
        // * Mappers for String source
        final Map<Class<?>, Mapper<String, Object>> stringMap = new HashMap<>(3);

        //   - Mapper for String (ISO 8601 format) to OracleJsonTimestamp, pattern "YYYY-MM-DDThh:mm:ss"
        stringMap.put(OracleJsonTimestamp.class, source -> {
            try {
                return factory.createTimestamp​(
                        LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });

        //   - Mapper for String (ISO 8601 format) to OracleJsonDate, pattern "YYYY-MM-DDThh:mm:ss"
        stringMap.put(OracleJsonDate.class, source -> {
            try {
                return factory.createDate​(
                        LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });

        //   - Mapper for String (ISO 8601 format) to OracleJsonTimestampTZ, pattern "YYYY-MM-DDThh:mm:ss+hh:mm"
        //     or "YYYY-MM-DDThh:mm:ss-hh:mm"
        stringMap.put(OracleJsonTimestampTZ.class, source -> {
            try {
                return factory.createTimestampTZ​(
                        OffsetDateTime.parse(source, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });

        mappers.put(
                String.class,
                Collections.unmodifiableMap(stringMap));

        return Collections.unmodifiableMap(mappers);
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

    private static String oracleJsonDateToString(final OracleJsonDate source) {
        return source.getString();
    }

}
