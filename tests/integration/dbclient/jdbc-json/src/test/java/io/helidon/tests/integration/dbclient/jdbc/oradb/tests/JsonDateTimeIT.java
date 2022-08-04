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

package io.helidon.tests.integration.dbclient.jdbc.oradb.tests;

import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbRow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.jdbc.oradb.init.InitIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.jdbc.oradb.init.InitIT.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test JSON date and time support.
 */
public class JsonDateTimeIT {

    private static final Logger LOGGER = Logger.getLogger(JsonDateTimeIT.class.getName());

    private static final MapperManager MAPPERS = MapperManager.create();

    public static final Map<Integer, JsonObject> POKEMONS = new HashMap<>();

    static {
        JsonObjectBuilder pokemonBuilder = Json.createObjectBuilder();
        // Pokemon for LocalDate tests
        pokemonBuilder.add("name", "Seviper");
        pokemonBuilder.add("type", "poison");
        pokemonBuilder.add("caught", MAPPERS.map(
                LocalDate.of(2020, 06, 18), LocalDate.class, String.class));
        POKEMONS.put(100, pokemonBuilder.build());
        pokemonBuilder = Json.createObjectBuilder();
        // Pokemon for LocalTime tests
        pokemonBuilder.add("name", "Palkia");
        pokemonBuilder.add("type", "dragon");
        pokemonBuilder.add("caught", MAPPERS.map(
                LocalTime.of(14, 36, 58), LocalTime.class, String.class));
        POKEMONS.put(101, pokemonBuilder.build());
        pokemonBuilder = Json.createObjectBuilder();
        // Pokemon for LocalDateTime tests
        pokemonBuilder.add("name", "Dialga");
        pokemonBuilder.add("type", "dragon");
        pokemonBuilder.add("caught", MAPPERS.map(
                LocalDateTime.of(2020, 06, 18, 14, 36, 58), LocalDateTime.class, String.class));
        POKEMONS.put(102, pokemonBuilder.build());
        // Pokemon for ZonedDateTime tests
        pokemonBuilder.add("name", "Lugia");
        pokemonBuilder.add("type", "flying");
        pokemonBuilder.add("caught", MAPPERS.map(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)),
                ZonedDateTime.class, String.class));
        POKEMONS.put(103, pokemonBuilder.build());
        // Pokemon for OffsetTime tests
        pokemonBuilder.add("name", "Ho-Oh");
        pokemonBuilder.add("type", "flying");
        pokemonBuilder.add("caught", MAPPERS.map(
                OffsetTime.of(
                        LocalTime.of(14, 36, 58),
                        ZoneOffset.ofHours(2)),
                OffsetTime.class, String.class));
        POKEMONS.put(104, pokemonBuilder.build());
        // Pokemon for OffsetDateTime tests
        pokemonBuilder.add("name", "Raikou");
        pokemonBuilder.add("type", "electric");
        pokemonBuilder.add("caught", MAPPERS.map(
                OffsetDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)),
                OffsetDateTime.class, String.class));
        POKEMONS.put(105, pokemonBuilder.build());
        // Pokemon for Instant tests
        pokemonBuilder.add("name", "Entei");
        pokemonBuilder.add("type", "fire");
        pokemonBuilder.add("caught", MAPPERS.map(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2))
                        .toInstant(),
                Instant.class, String.class));
        POKEMONS.put(106, pokemonBuilder.build());
         // Pokemon for OffsetDateTime tests
        pokemonBuilder.add("name", "Suicune");
        pokemonBuilder.add("type", "water");
        pokemonBuilder.add("caught", MAPPERS.map(
                Date.from(
                        ZonedDateTime.of(
                                LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                                ZoneOffset.ofHours(2)
                        ).toInstant()),
                Date.class, String.class));
        POKEMONS.put(107, pokemonBuilder.build());
        // Pokemon for Instant tests
        pokemonBuilder.add("name", "Celebi");
        pokemonBuilder.add("type", "grass");
        pokemonBuilder.add("caught", MAPPERS.map(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2))
                        .toInstant(),
                Instant.class, String.class));
        POKEMONS.put(108, pokemonBuilder.build());
   }

    @BeforeAll
    static void setup() {
        POKEMONS.forEach((id, pokemon) -> {
        StringWriter sw = new StringWriter(128);
        try (JsonWriter jw = Json.createWriter(sw)) {
            jw.write(pokemon);
        }
        String pokemonStr = sw.toString();
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", id, pokemonStr)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        });
    }

    @Test
    void testJsonLocalDateSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 100)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        LocalDate caught = row.column("caught").as(LocalDate.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date: %s", caught));
        assertThat(caught, equalTo(LocalDate.of(2020, 06, 18)));
    }

    @Test
    void testJsonLocalTimeSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 101)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        LocalTime caught = row.column("caught").as(LocalTime.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught time: %s", caught));
        assertThat(caught, equalTo(LocalTime.of(14, 36, 58)));
    }

    @Test
    void testJsonLocalDateTimeSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 102)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        LocalDateTime caught = row.column("caught").as(LocalDateTime.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(LocalDateTime.of(2020, 06, 18, 14, 36, 58)));
    }

    @Test
    void testJsonZonedDateTimeSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 103)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        ZonedDateTime caught = row.column("caught").as(ZonedDateTime.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2))));
    }


     @Test
    void testJsonOffsetTimeSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 104)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        OffsetTime caught = row.column("caught").as(OffsetTime.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(
                OffsetTime.of(
                        LocalTime.of(14, 36, 58),
                        ZoneOffset.ofHours(2))));
    }

    @Test
    void testJsonOffsetDateTimeSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 105)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        OffsetDateTime caught = row.column("caught").as(OffsetDateTime.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(
                OffsetDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2))));
    }

    @Test
    void testJsonInstantSelect() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 106)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        Instant caught = row.column("caught").as(Instant.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)
                ).toInstant()));
    }

    @Test
    void testJsonUtilDateSelectFromZonedDateTime() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 107)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        Date caught = row.column("caught").as(Date.class);
        Date verify = Date.from(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)
                ).toInstant());
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(verify));
    }

    @Test
    void testJsonUtilDateSelectFromInstant() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 108)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        Date caught = row.column("caught").as(Date.class);
        Date verify = Date.from(
                ZonedDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)
                ).toInstant());
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date and time: %s", caught));
        assertThat(caught, equalTo(verify));
    }

}
