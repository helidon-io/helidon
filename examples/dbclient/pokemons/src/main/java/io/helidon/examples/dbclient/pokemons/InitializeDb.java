/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.dbclient.pokemons;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Initialize JDBC database schema and populate it with sample data.
 */
public class InitializeDb {

    /**
     * Pokemon types source file.
     */
    private static final String TYPES = "/Types.json";
    /**
     * Pokemons source file.
     */
    private static final String POKEMONS = "/Pokemons.json";

    /**
     * Initialize JDBC database schema and populate it with sample data.
     *
     * @param dbClient   database client
     * @param initSchema {@code true} if schema should be initialized
     */
    static void init(DbClient dbClient, boolean initSchema) {
        try {
            if (initSchema) {
                initSchema(dbClient);
            }
            initData(dbClient);
        } catch (Exception ex) {
            System.out.printf("Could not initialize database: %s\n", ex.getMessage());
        }
    }

    /**
     * Initializes database schema (tables).
     *
     * @param dbClient database client
     */
    private static void initSchema(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        try {
            exec.namedDml("create-types");
            exec.namedDml("create-pokemons");
        } catch (Exception ex1) {
            System.out.printf("Could not create tables: %s", ex1.getMessage());
            try {
                deleteData(dbClient);
            } catch (Exception ex2) {
                System.out.printf("Could not delete tables: %s", ex2.getMessage());
            }
        }
    }

    /**
     * InitializeDb database content (rows in tables).
     *
     * @param dbClient database client
     */
    private static void initData(DbClient dbClient) {
        // Init Pokémon types
        DbExecute exec = dbClient.execute();
        initTypes(exec);
        initPokemons(exec);
    }

    /**
     * Delete content of all tables.
     *
     * @param dbClient database client
     */
    private static void deleteData(DbClient dbClient) {
        DbExecute exec = dbClient.execute();
        exec.namedDelete("delete-all-pokemons");
        exec.namedDelete("delete-all-types");
    }

    /**
     * Initialize Pokémon types.
     * Source data file is JSON file containing array of type objects:
     * <pre>
     * [
     *   { "id": <type_id>, "name": <type_name> },
     *   ...
     * ]
     * </pre>
     * where {@code id} is JSON number and {@code name} is JSON String.
     *
     * @param exec database client executor
     */
    private static void initTypes(DbExecute exec) {
        try (JsonReader reader = Json.createReader(InitializeDb.class.getResourceAsStream(TYPES))) {
            JsonArray types = reader.readArray();
            for (JsonValue typeValue : types) {
                JsonObject type = typeValue.asJsonObject();
                exec.namedInsert("insert-type",
                        type.getInt("id"),
                        type.getString("name"));
            }
        }
    }

    /**
     * Initialize Pokémon.
     * Source data file is JSON file containing array of type objects:
     * <pre>
     * [
     *   { "id": <type_id>, "name": <type_name>, "type": [<type_id>, <type_id>, ...] },
     *   ...
     * ]
     * </pre>
     * where {@code id} is JSON number and {@code name} is JSON String.
     *
     * @param exec database client executor
     */
    private static void initPokemons(DbExecute exec) {
        try (JsonReader reader = Json.createReader(InitializeDb.class.getResourceAsStream(POKEMONS))) {
            JsonArray pokemons = reader.readArray();
            for (JsonValue pokemonValue : pokemons) {
                JsonObject pokemon = pokemonValue.asJsonObject();
                exec.namedInsert("insert-pokemon",
                        pokemon.getInt("id"),
                        pokemon.getString("name"),
                        pokemon.getInt("idType"));
            }
        }
    }

    /**
     * Creates an instance of database initialization.
     */
    private InitializeDb() {
        throw new UnsupportedOperationException("Instances of InitializeDb utility class are not allowed");
    }

}
