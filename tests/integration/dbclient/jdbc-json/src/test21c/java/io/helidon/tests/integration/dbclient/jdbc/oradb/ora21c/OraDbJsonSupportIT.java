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

package io.helidon.tests.integration.dbclient.jdbc.oradb.ora21c;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbRow;

import oracle.sql.json.OracleJsonDate;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonTimestamp;
import oracle.sql.json.OracleJsonTimestampTZ;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.jdbc.oradb.init.InitIT.DB_CLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// ./test.sh -t /data/heli-jsondb/oradb_wallet -u jdbc:oracle:thin:@db202106011502_high -d ora21c -e
/**
 * Test Oracle 21c specific  features
 */
public class OraDbJsonSupportIT {

    private static final Logger LOGGER = Logger.getLogger(OraDbJsonSupportIT.class.getName());

    private static final MapperManager MAPPERS = MapperManager.create();

    @BeforeAll
    static void setup() {
        // JSON object for select tests
        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject pokemon = factory.createObject();
        pokemon.put("name", "Igglybuff");
        pokemon.put("type", "fairy");
        pokemon.put("caught", factory.createDate(LocalDateTime.of(2020, 06, 18, 9, 33)));
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1010, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
    }

    @Test
    void testInsertPokemonFromJsonObject() {
        // Insert JSON object from JsonObject instance
        JsonObjectBuilder pokemonBuilder = Json.createObjectBuilder();
        pokemonBuilder.add("name", "Diglett");
        pokemonBuilder.add("type", "ground");
        pokemonBuilder.add("cp", 624);
        pokemonBuilder.add("evolve", true);
        JsonObject pokemon = pokemonBuilder.build();
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1011, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        // Verify content of stored JSON
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-by-id", 1011)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject dbPokemon = row.column("json").as(JsonObject.class);
        assertThat(dbPokemon.getString("name"), equalTo("Diglett"));
    }

    @Test
    void testOracleJsonDateStorage() {
        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject pokemon = factory.createObject();
        pokemon.put("name", "Jigglypuff");
        pokemon.put("type", "fairy");
        pokemon.put("caught", factory.createDate(LocalDateTime.of(2020, 05, 21, 14, 21)));
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1012, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        // Verify content of stored JSON
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-by-id", 1012)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject dbPokemon = row.column("json").as(JsonObject.class);
        assertThat(dbPokemon.getString("name"), equalTo("Jigglypuff"));
        assertThat(dbPokemon.getString("caught"), equalTo("2020-05-21T14:21:00"));
    }

    @Test
    void testOracleJsonDateSelectString() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 1010)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        String caught = row.column("caught").as(String.class);
        LOGGER.log(Level.FINEST, () -> String.format("Returned caught date: %s", caught));
        assertThat(caught, equalTo("2020-06-18T09:33:00"));
    }

    @Test
    void testOracleJsonDateSelectUtilDate() {
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-caught", 1010)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        Date caught = row.column("caught").as(Date.class);
        LocalDateTime checkLdt = LocalDateTime.of(2020, 6, 18, 9, 33);
        Date checkDate = java.util.Date.from(checkLdt.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(caught, equalTo(checkDate));
    }

    // Store OracleJsonTimestamp and verify it's JSON value with OracleJsonTimestamp mapper conversion
    @Test
    void testOracleOracleJsonTimestampStorage() {
        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject pokemon = factory.createObject();
        OracleJsonTimestamp caughtIn = factory.createTimestamp​(LocalDateTime.of(2020, 05, 21, 14, 21));
        pokemon.put("name", "Hoothoot");
        pokemon.put("type", "flying");
        pokemon.put("caught", caughtIn);
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1013, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        // Verify content of stored JSON
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-by-id", 1013)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject dbPokemon = row.column("json").as(JsonObject.class);
        assertThat(dbPokemon.getString("name"), equalTo("Hoothoot"));
        OracleJsonTimestamp caughtOut = MAPPERS.map(
                dbPokemon.getString("caught"), String.class, OracleJsonTimestamp.class);
        assertThat(caughtIn, equalTo(caughtOut));
    }

    // Store OracleJsonDate and verify it's JSON value with OracleJsonDate mapper conversion
    @Test
    void testOracleOracleJsonDateStorage() {
        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject pokemon = factory.createObject();
        OracleJsonDate caughtIn = factory.createDate(LocalDateTime.of(2020, 05, 21, 14, 21));
        pokemon.put("name", "Slowking");
        pokemon.put("type", "psychic");
        pokemon.put("caught", caughtIn);
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1014, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        // Verify content of stored JSON
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-by-id", 1014)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject dbPokemon = row.column("json").as(JsonObject.class);
        assertThat(dbPokemon.getString("name"), equalTo("Slowking"));
        OracleJsonDate caughtOut = MAPPERS.map(
                dbPokemon.getString("caught"), String.class, OracleJsonDate.class);
        assertThat(caughtIn, equalTo(caughtOut));
    }

        // Store OracleJsonDate and verify it's JSON value with OracleJsonDate mapper conversion
    @Test
    void testOracleOracleJsonTimestampTZStorage() {
        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject pokemon = factory.createObject();
        OracleJsonTimestampTZ caughtIn = factory.createTimestampTZ(
                OffsetDateTime.of(
                        LocalDateTime.of(2020, 06, 18, 14, 36, 58),
                        ZoneOffset.ofHours(2)));
        pokemon.put("name", "Slowbro");
        pokemon.put("type", "psychic");
        pokemon.put("caught", caughtIn);
        try {
            DB_CLIENT.execute(exec -> exec
                    .namedDml("insert-doc", 1015, pokemon)
            ).await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data initialization failed: %s", ex.getMessage()));
            throw ex;
        }
        // Verify content of stored JSON
        DbRow row;
        try {
            row = DB_CLIENT.execute(exec -> exec
                    .namedQuery("select-pokemon-by-id", 1015)
            ).first().await();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, ex, () -> String.format("Data query failed: %s", ex.getMessage()));
            throw ex;
        }
        JsonObject dbPokemon = row.column("json").as(JsonObject.class);
        assertThat(dbPokemon.getString("name"), equalTo("Slowbro"));
        OracleJsonTimestampTZ caughtOut = MAPPERS.map(
                dbPokemon.getString("caught"), String.class, OracleJsonTimestampTZ.class);
        assertThat(caughtIn, equalTo(caughtOut));
    }

}
