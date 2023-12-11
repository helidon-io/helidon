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
package io.helidon.examples.dbclient.common;

import io.helidon.common.parameters.Parameters;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.http.NotFoundException;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

/**
 * Common methods that do not differ between JDBC and MongoDB.
 */
public abstract class AbstractPokemonService implements HttpService {

    private final DbClient dbClient;

    /**
     * Create a new Pokémon service with a DB client.
     *
     * @param dbClient DB client to use for database operations
     */
    protected AbstractPokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }


    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::listPokemons)
                // create new
                .put("/", Handler.create(Pokemon.class, this::insertPokemon))
                // update existing
                .post("/{name}/type/{type}", this::insertPokemonSimple)
                // delete all
                .delete("/", this::deleteAllPokemons)
                // get one
                .get("/{name}", this::getPokemon)
                // delete one
                .delete("/{name}", this::deletePokemon)
                // example of transactional API (local transaction only!)
                .put("/transactional", this::transactional)
                // update one (TODO this is intentionally wrong - should use JSON request, just to make it simple we use path)
                .put("/{name}/type/{type}", this::updatePokemonType);
    }

    /**
     * The DB client associated with this service.
     *
     * @return DB client instance
     */
    protected DbClient dbClient() {
        return dbClient;
    }

    /**
     * This method is left unimplemented to show differences between native statements that can be used.
     *
     * @param req Server request
     * @param res Server response
     */
    protected abstract void deleteAllPokemons(ServerRequest req, ServerResponse res);

    /**
     * Insert new Pokémon with specified name.
     *
     * @param pokemon pokemon request entity
     * @param res     the server response
     */
    private void insertPokemon(Pokemon pokemon, ServerResponse res) {
        long count = dbClient.execute().createNamedInsert("insert2")
                .namedParam(pokemon)
                .execute();
        res.send("Inserted: " + count + " values");
    }

    /**
     * Insert new Pokémon with specified name.
     *
     * @param req the server request
     * @param res the server response
     */
    private void insertPokemonSimple(ServerRequest req, ServerResponse res) {
        Parameters params = req.path().pathParameters();
        // Test Pokémon POJO mapper
        Pokemon pokemon = new Pokemon(params.get("name"), params.get("type"));

        long count = dbClient.execute().createNamedInsert("insert2")
                .namedParam(pokemon)
                .execute();
        res.send("Inserted: " + count + " values");
    }

    /**
     * Get a single Pokémon by name.
     *
     * @param req server request
     * @param res server response
     */
    private void getPokemon(ServerRequest req, ServerResponse res) {
        String pokemonName = req.path().pathParameters().get("name");
        res.send(dbClient.execute()
                .namedGet("select-one", pokemonName)
                .orElseThrow(() -> new NotFoundException("Pokemon " + pokemonName + " not found"))
                .as(JsonObject.class));
    }

    /**
     * Return JsonArray with all stored Pokémon.
     *
     * @param req the server request
     * @param res the server response
     */
    private void listPokemons(ServerRequest req, ServerResponse res) {
        res.send(dbClient.execute()
                .namedQuery("select-all")
                .map(it -> it.as(JsonObject.class))
                .toList());
    }

    /**
     * Update a Pokémon.
     * Uses a transaction.
     *
     * @param req the server request
     * @param res the server response
     */
    private void updatePokemonType(ServerRequest req, ServerResponse res) {
        Parameters params = req.path().pathParameters();
        String name = params.get("name");
        String type = params.get("type");
        long count = dbClient.execute()
                .createNamedUpdate("update")
                .addParam("name", name)
                .addParam("type", type)
                .execute();
        res.send("Updated: " + count + " values");
    }

    private void transactional(ServerRequest req, ServerResponse res) {
        Pokemon pokemon = req.content().as(Pokemon.class);
        DbTransaction tx = dbClient.transaction();
        try {
            long count = tx.createNamedGet("select-for-update")
                    .namedParam(pokemon)
                    .execute()
                    .map(dbRow -> tx.createNamedUpdate("update")
                            .namedParam(pokemon)
                            .execute())
                    .orElse(0L);
            tx.commit();
            res.send("Updated " + count + " records");
        } catch (Throwable t) {
            tx.rollback();
            throw t;
        }
    }

    /**
     * Delete a Pokémon with specified name (key).
     *
     * @param req the server request
     * @param res the server response
     */
    private void deletePokemon(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        long count = dbClient.execute().namedDelete("delete", name);
        res.send("Deleted: " + count + " values");
    }
}
