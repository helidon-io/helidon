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

package io.helidon.integrations.db.pgsql;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import io.helidon.common.mapper.Mapper;
import io.helidon.common.mapper.spi.MapperProvider;

import org.postgresql.util.PGobject;

/**
 * Java Service loader service to get PostgreSQL database types mappers.
 */
public class DbClientPgSqlMapperProvider implements MapperProvider {

    private static final Logger LOGGER = Logger.getLogger(DbClientPgSqlMapperProvider.class.getName());

    //Mappers index {@code [Class<SOURCE>, Class<TARGET>] -> Mapper<SOURCE, TARGET>}.
    private static final Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> MAPPERS = initMappers();

    // initialize mappers instances
    private static Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> initMappers() {
        Map<Class<?>, Map<Class<?>, Mapper<?, ?>>> mappers = new HashMap<>(2);
        // * Mappers for JsonValue source
        Map<Class<?>, Mapper<PGobject, Object>> pgObjectMap = new HashMap<>(1);

        //   - Mapper for PGobject to JsonArray
        pgObjectMap.put(JsonArray.class, source -> {
            final String jsonString = source.getValue();
            if (jsonString == null) {
            LOGGER.finest(() -> String.format("PgObject [%s]: null", source.getType()));
                return JsonValue.NULL;
            }
            LOGGER.finest(() -> String.format("PgObject [%s]: %s", source.getType(), jsonString));
            try (JsonReader jr = Json.createReader(new StringReader(jsonString))) {
                return jr.readArray();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t,
                        () -> String.format("PgObject to JsonArray mapping failed for %s: %s", source, t.getMessage()));
                throw t;
            }
        });

        //   - Mapper for PGobject to JsonObject
        pgObjectMap.put(JsonObject.class, source -> {
            final String jsonString = source.getValue();
            if (jsonString == null) {
            LOGGER.finest(() -> String.format("PgObject [%s]: null", source.getType()));
                return JsonValue.NULL;
            }
            LOGGER.finest(() -> String.format("PgObject [%s]: %s", source.getType(), jsonString));
            try (JsonReader jr = Json.createReader(new StringReader(jsonString))) {
                return jr.readObject();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t,
                        () -> String.format("PgObject to JsonObject mapping failed for %s: %s", source, t.getMessage()));
                throw t;
            }
        });

        //   - Mapper for PGobject to JsonValue
        pgObjectMap.put(JsonValue.class, source -> {
            final String jsonString = source.getValue();
            if (jsonString == null) {
            LOGGER.finest(() -> String.format("PgObject [%s]: null", source.getType()));
                return JsonValue.NULL;
            }
            LOGGER.finest(() -> String.format("PgObject [%s]: %s", source.getType(), jsonString));
            try (JsonReader jr = Json.createReader(new StringReader(jsonString))) {
                return jr.readValue();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t,
                        () -> String.format("PgObject to JsonValue mapping failed for %s: %s", source, t.getMessage()));
                throw t;
            }
        });

        mappers.put(
                PGobject.class,
                Collections.unmodifiableMap(pgObjectMap));

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

}
