/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;

/**
 * Initialize JDBC database schema and populate it with sample data.
 */
public class InitializeDb {

    /** Pokemon types source file. */
    private static final String TYPES = "/Types.json";
    /** Pokemons source file. */
    private static final String POKEMONS = "/Pokemons.json";

    /**
     * Initialize JDBC database schema and populate it with sample data.
     * @param dbClient database client
     */
    static void init(DbClient dbClient) {
        try {
            if (!PokemonMain.mongo) {
                initSchema(dbClient);
            }
            initData(dbClient);
        } catch (ExecutionException | InterruptedException ex) {
            System.out.printf("Could not initialize database: %s", ex.getMessage());
        }
    }

    /**
     * Initializes database schema (tables).
     *
     * @param dbClient database client
     */
    private static void initSchema(DbClient dbClient) {
        try {
            dbClient.execute(exec -> exec
                    .namedDml("create-types")
                    .thenCompose(result -> exec.namedDml("create-pokemons"))
            ).toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException ex1) {
            System.out.printf("Could not create tables: %s", ex1.getMessage());
            try {
                deleteData(dbClient);
            } catch (ExecutionException | InterruptedException ex2) {
                System.out.printf("Could not delete tables: %s", ex2.getMessage());
            }
        }
    }

    /**
     * InitializeDb database content (rows in tables).
     *
     * @param dbClient database client
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void initData(DbClient dbClient) throws InterruptedException, ExecutionException {
        // Init pokemon types
        dbClient.execute(exec
                -> initTypes(exec)
                        .thenCompose(count -> initPokemons(exec)))
                .toCompletableFuture().get();
    }

    /**
     * Delete content of all tables.
     *
     * @param dbClient database client
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void deleteData(DbClient dbClient) throws InterruptedException, ExecutionException {
        dbClient.execute(exec -> exec
                .namedDelete("delete-all-pokemons")
                .thenCompose(count -> exec.namedDelete("delete-all-types")))
                .toCompletableFuture()
                .get();
    }

    /**
     * Initialize pokemon types.
     * Source data file is JSON file containing array of type objects:
     *<pre>
     * [
     *   { "id": <type_id>, "name": <type_name> },
     *   ...
     * ]
     * </pre>
     * where {@code id} is JSON number and {@ocde name} is JSON String.
     *
     * @param exec database client executor
     * @return executed statements future
     */
    private static CompletionStage<Long> initTypes(DbExecute exec) {
        CompletionStage<Long> stage = null;
        try (javax.json.JsonReader reader = javax.json.Json.createReader(InitializeDb.class.getResourceAsStream(TYPES))) {
            javax.json.JsonArray types = reader.readArray();
            for (javax.json.JsonValue typeValue : types) {
                javax.json.JsonObject type = typeValue.asJsonObject();
                stage = stage == null
                        ? exec.namedInsert("insert-type", type.getInt("id"), type.getString("name"))
                        : stage.thenCompose(result -> exec.namedInsert(
                            "insert-type", type.getInt("id"), type.getString("name")));
            }
        }
        return stage;
    }

    /**
     * Initialize pokemos.
     * Source data file is JSON file containing array of type objects:
     *<pre>
     * [
     *   { "id": <type_id>, "name": <type_name>, "type": [<type_id>, <type_id>, ...] },
     *   ...
     * ]
     * </pre>
     * where {@code id} is JSON number and {@ocde name} is JSON String.
     *
     * @param exec database client executor
     * @return executed statements future
     */
    private static CompletionStage<Long> initPokemons(DbExecute exec) {
        CompletionStage<Long> stage = null;
        try (javax.json.JsonReader reader = javax.json.Json.createReader(InitializeDb.class.getResourceAsStream(POKEMONS))) {
            javax.json.JsonArray pokemons = reader.readArray();
            for (javax.json.JsonValue pokemonValue : pokemons) {
                javax.json.JsonObject pokemon = pokemonValue.asJsonObject();
                stage = stage == null
                        ? exec
                                .namedInsert("insert-pokemon",
                                        pokemon.getInt("id"), pokemon.getString("name"), pokemon.getInt("idType"))
                        : stage.thenCompose(result -> exec
                                .namedInsert("insert-pokemon",
                                        pokemon.getInt("id"), pokemon.getString("name"), pokemon.getInt("idType")));
            }
        }
        return stage;
    }

    /**
     * Creates an instance of database initialization.
     */
    private InitializeDb() {
        throw new UnsupportedOperationException("Instances of InitializeDb utility class are not allowed");
    }

}
