/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutionException;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;

/**
 * Initialize JDBC database schema and populate it with sample data.
 */
public class InitializeDb {

    /**
     * Pokemon types source file.
     * */
    private static final String TYPES = "/Types.json";

    /**
     * Pokemons source file.
     * */
    private static final String POKEMONS = "/Pokemons.json";

    /**
     * Initialize JDBC database schema and populate it with sample data.
     *
     * @param dbClient database client
     */
    static void init(DbClient dbClient) {
        try {
            initSchema(dbClient);
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
                    .flatMapSingle(result -> exec.namedDml("create-pokemons")))
                    .await();
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
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    private static void initData(DbClient dbClient) throws InterruptedException, ExecutionException {
        // Init pokemon types
        dbClient.execute(exec
                -> initTypes(exec)
                        .flatMapSingle(count -> initPokemons(exec)))
                .toCompletableFuture().get();
    }

    /**
     * Delete content of all tables.
     *
     * @param dbClient database client
     */
    private static void deleteData(DbClient dbClient) {
        dbClient.execute(exec -> exec
                .namedDelete("delete-all-pokemons")
                .flatMapSingle(count -> exec.namedDelete("delete-all-types")))
                .await();
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
    private static Single<Long> initTypes(DbExecute exec) {
        Single<Long> stage = Single.just(0L);
        try (javax.json.JsonReader reader = javax.json.Json.createReader(InitializeDb.class.getResourceAsStream(TYPES))) {
            javax.json.JsonArray types = reader.readArray();
            for (javax.json.JsonValue typeValue : types) {
                javax.json.JsonObject type = typeValue.asJsonObject();
                stage = stage.flatMapSingle(it -> exec.namedInsert(
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
    private static Single<Long> initPokemons(DbExecute exec) {
        Single<Long> stage = Single.just(0L);
        try (javax.json.JsonReader reader = javax.json.Json.createReader(InitializeDb.class.getResourceAsStream(POKEMONS))) {
            javax.json.JsonArray pokemons = reader.readArray();
            for (javax.json.JsonValue pokemonValue : pokemons) {
                javax.json.JsonObject pokemon = pokemonValue.asJsonObject();
                stage = stage.flatMapSingle(result -> exec
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
