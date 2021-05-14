/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.dbclient.jsonp;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.dbclient.DbClientException;

/**
 *
 */
public class JsonProcessingMapperProvider implements MapperProvider {

    private static final Logger LOGGER = Logger.getLogger(JsonProcessingMapperProvider.class.getName());

    //Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();

    // initialize mappers instances
    private static Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> initMappers() {

        final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> mappers = new HashMap<>(2);

        mappers.put(String.class, stringMappers());

        // * Mappers for LocalDate source
        final Map<Class<?>, Mapper<LocalDate, Object>> localDateMap = new HashMap<>(1);

        //   - Mapper for LocalDate (ISO 8601 format), pattern "YYYY-MM-DD" to String
        localDateMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE);
        });

        mappers.put(
                LocalDate.class,
                Collections.unmodifiableMap(localDateMap));

        // * Mappers for LocalTime source
        final Map<Class<?>, Mapper<LocalTime, Object>> localTimeMap = new HashMap<>(1);

        //   - Mapper for LocalTime (ISO 8601 format), pattern "hh:mm:ss" to String
        localTimeMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_LOCAL_TIME);
        });

        mappers.put(
                LocalTime.class,
                Collections.unmodifiableMap(localTimeMap));

        // * Mappers for LocalDateTime source
        final Map<Class<?>, Mapper<LocalDateTime, Object>> localDateTimeMap = new HashMap<>(1);

        //   - Mapper for LocalDateTime (ISO 8601 format), pattern "YYYY-MM-DDThh:mm:ss" to String
        localDateTimeMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        });

        mappers.put(
                LocalDateTime.class,
                Collections.unmodifiableMap(localDateTimeMap));

        // * Mappers for ZonedDateTime source
        final Map<Class<?>, Mapper<ZonedDateTime, Object>> zonedDateTimeMap = new HashMap<>(1);

        //   - Mapper for ZonedDateTime (ISO 8601 format), pattern "YYYY-MM-DDThh:mm:ss+hh:mm"
        //     or "YYYY-MM-DDThh:mm:ss-hh:mm" to String
        zonedDateTimeMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        });

        mappers.put(
                ZonedDateTime.class,
                Collections.unmodifiableMap(zonedDateTimeMap));

        // * Mappers for OffsetTime source
        final Map<Class<?>, Mapper<OffsetTime, Object>> offsetTimeMap = new HashMap<>(1);

        //   - Mapper for OffsetTime (ISO 8601 format), pattern "hh:mm:ss+hh:mm" or "hh:mm:ss-hh:mm" to String
        offsetTimeMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_OFFSET_TIME);
        });

        mappers.put(
                OffsetTime.class,
                Collections.unmodifiableMap(offsetTimeMap));

        // * Mappers for OffsetDateTime source
        final Map<Class<?>, Mapper<OffsetDateTime, Object>> offsetDateTimeMap = new HashMap<>(1);

        //   - Mapper for OffsetDateTime (ISO 8601 format), pattern "YYYY-MM-DDThh:mm:ss+hh:mm"
        //     or "YYYY-MM-DDThh:mm:ss-hh:mm" to String
        offsetDateTimeMap.put(String.class, source -> {
            return source.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        });

        mappers.put(
                OffsetDateTime.class,
                Collections.unmodifiableMap(offsetDateTimeMap));

        // * Mappers for Instant source
        final Map<Class<?>, Mapper<Instant, Object>> instantMap = new HashMap<>(1);

        //   - Mapper for Instant (ISO 8601 format), pattern "YYYY-MM-DDThh:mm:ssZ" to String
        instantMap.put(String.class, source -> {
            return  DateTimeFormatter.ISO_INSTANT.format(source);
        });

        mappers.put(
                Instant.class,
                Collections.unmodifiableMap(instantMap));

        // * Mappers for java.util.Date source
        final Map<Class<?>, Mapper<Date, Object>> utilDateMap = new HashMap<>(1);

        //   - Mapper for java.util.Date (ISO 8601 format), pattern "YYYY-MM-DDThh:mm:ssZ" to String
        utilDateMap.put(String.class, source -> {
            return DateTimeFormatter.ISO_INSTANT
                    .withZone(ZoneId.of("UTC"))
                    .format(source.toInstant());
        });

        mappers.put(
                Date.class,
                Collections.unmodifiableMap(utilDateMap));

        // * Mappers for JsonValue source
        final Map<Class<?>, Mapper<JsonValue, Object>> jsonValueMap = new HashMap<>(1);

        //   - Mapper for JsonValue to String
        jsonValueMap.put(String.class, source -> {
            final StringWriter sw = new StringWriter();
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(source);
            }
            return sw.toString();
        });

        mappers.put(
                JsonValue.class,
                Collections.unmodifiableMap(jsonValueMap));

        return Collections.unmodifiableMap(mappers);
    }

    // Mappers for String source
    private static Map<Class<?>, Mapper<?, ?>> stringMappers() {
        final Map<Class<?>, Mapper<String, Object>> stringMap = new HashMap<>(3);

        //   - Mapper for String to JsonValue
        stringMap.put(JsonValue.class, source -> {
            try (JsonReader jr = Json.createReader(new StringReader(source))) {
                return jr.readValue();
            }
        });
        //   - Mapper for String to JsonArray
        stringMap.put(JsonArray.class, source -> {
            try (JsonReader jr = Json.createReader(new StringReader(source))) {
                return jr.readArray();
            }
        });
        //   - Mapper for String to JsonObject
        stringMap.put(JsonObject.class, source -> {
            try (JsonReader jr = Json.createReader(new StringReader(source))) {
                return jr.readObject();
            }
        });
        //   - Mapper for String (ISO 8601 format) to java.util.Date
        stringMap.put(Date.class, source -> {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            TemporalAccessor parsed;
            Instant instant;
            try {
                parsed = formatter.parseBest(
                        source,
                        ZonedDateTime::from,
                        LocalDateTime::from,
                        Instant::from);
                 if (parsed instanceof LocalDateTime) {
                     instant = ((LocalDateTime) parsed).atZone(ZoneId.systemDefault()).toInstant();
                 } else {
                     instant = Instant.from(parsed);
                 }
                 return Date.from(instant);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s", source));
            }
        });
        //   - Mapper for String (ISO 8601 format) to LocalDate, pattern "YYYY-MM-DD"
        stringMap.put(LocalDate.class, source -> {
            try {
                return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to LocalTime, pattern "hh:mm:ss"
        stringMap.put(LocalTime.class, source -> {
            try {
                return LocalTime.parse(source, DateTimeFormatter.ISO_LOCAL_TIME);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to LocalDateTime, pattern "YYYY-MM-DDThh:mm:ss"
        stringMap.put(LocalDateTime.class, source -> {
            try {
                return LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to ZonedDateTime, pattern "YYYY-MM-DDThh:mm:ss+hh:mm"
        //     or "YYYY-MM-DDThh:mm:ss-hh:mm"
        stringMap.put(ZonedDateTime.class, source -> {
            try {
                return ZonedDateTime.parse(source, DateTimeFormatter.ISO_ZONED_DATE_TIME);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to OffsetTime, pattern "hh:mm:ss+hh:mm" or "hh:mm:ss-hh:mm"
        stringMap.put(OffsetTime.class, source -> {
            try {
                return OffsetTime.parse(source, DateTimeFormatter.ISO_OFFSET_TIME);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to OffsetDateTime, pattern "YYYY-MM-DDThh:mm:ss+hh:mm"
        //     or "YYYY-MM-DDThh:mm:ss-hh:mm"
        stringMap.put(OffsetDateTime.class, source -> {
            try {
                return OffsetDateTime.parse(source, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        //   - Mapper for String (ISO 8601 format) to Instant, pattern "YYYY-MM-DDThh:mm:ssZ"
        stringMap.put(Instant.class, source -> {
            try {
                return Instant.parse(source);
            } catch (DateTimeParseException pe) {
                throw new DbClientException(
                        String.format("String to java.util.Date mapping failed for %s: %s", source, pe.getMessage()), pe);
            }
        });
        return Collections.unmodifiableMap(stringMap);
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

}
