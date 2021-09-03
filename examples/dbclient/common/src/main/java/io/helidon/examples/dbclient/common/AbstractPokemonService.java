/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Common methods that do not differ between JDBC and MongoDB.
 */
public abstract class AbstractPokemonService implements Service {
    private static final Logger LOGGER = Logger.getLogger(AbstractPokemonService.class.getName());

    private final DbClient dbClient;

    /**
     * Create a new pokemon service with a DB client.
     *
     * @param dbClient DB client to use for database operations
     */
    protected AbstractPokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void update(Routing.Rules rules) {
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
                .put("/transactional", Handler.create(Pokemon.class, this::transactional))
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
     * @param request Server request
     * @param response Server response
     */
    protected abstract void deleteAllPokemons(ServerRequest request, ServerResponse response);

    /**
     * Insert new pokemon with specified name.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void insertPokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        dbClient.execute(exec -> exec
                .createNamedInsert("insert2")
                .namedParam(pokemon)
                .execute())
                .thenAccept(count -> response.send("Inserted: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Insert new pokemon with specified name.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void insertPokemonSimple(ServerRequest request, ServerResponse response) {
        // Test Pokemon POJO mapper
        Pokemon pokemon = new Pokemon(request.path().param("name"), request.path().param("type"));

        dbClient.execute(exec -> exec
                .createNamedInsert("insert2")
                .namedParam(pokemon)
                .execute())
                .thenAccept(count -> response.send("Inserted: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Get a single pokemon by name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemon(ServerRequest request, ServerResponse response) {
        String pokemonName = request.path().param("name");

        dbClient.execute(exec -> exec.namedGet("select-one", pokemonName))
                .thenAccept(opt -> opt.ifPresentOrElse(it -> sendRow(it, response),
                                                       () -> sendNotFound(response, "Pokemon "
                                                               + pokemonName
                                                               + " not found")))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Return JsonArray with all stored pokemons or pokemons with matching attributes.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listPokemons(ServerRequest request, ServerResponse response) {
        Multi<JsonObject> rows = dbClient.execute(exec -> exec.namedQuery("select-all"))
                .map(it -> it.as(JsonObject.class));

        response.send(rows, JsonObject.class);
    }

    /**
     * Update a pokemon.
     * Uses a transaction.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updatePokemonType(ServerRequest request, ServerResponse response) {
        final String name = request.path().param("name");
        final String type = request.path().param("type");

        dbClient.execute(exec -> exec
                .createNamedUpdate("update")
                .addParam("name", name)
                .addParam("type", type)
                .execute())
                .thenAccept(count -> response.send("Updated: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    private void transactional(ServerRequest request, ServerResponse response, Pokemon pokemon) {

        dbClient.inTransaction(tx -> tx
                .createNamedGet("select-for-update")
                .namedParam(pokemon)
                .execute()
                .flatMapSingle(maybeRow -> maybeRow.map(dbRow -> tx.createNamedUpdate("update")
                        .namedParam(pokemon).execute())
                        .orElseGet(() -> Single.just(0L)))
        ).thenAccept(count -> response.send("Updated " + count + " records"));

    }

    /**
     * Delete pokemon with specified name (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    private void deletePokemon(ServerRequest request, ServerResponse response) {
        final String name = request.path().param("name");

        dbClient.execute(exec -> exec.namedDelete("delete", name))
                .thenAccept(count -> response.send("Deleted: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Send a 404 status code.
     *
     * @param response the server response
     * @param message entity content
     */
    protected void sendNotFound(ServerResponse response, String message) {
        response.status(Http.Status.NOT_FOUND_404);
        response.send(message);
    }

    /**
     * Send a single DB row as JSON object.
     *
     * @param row row as read from the database
     * @param response server response
     */
    protected void sendRow(DbRow row, ServerResponse response) {
        response.send(row.as(JsonObject.class));
    }

    /**
     * Send a 500 response code and a few details.
     *
     * @param throwable throwable that caused the issue
     * @param response server response
     * @param <T> type of expected response, will be always {@code null}
     * @return {@code Void} so this method can be registered as a lambda
     *      with {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}
     */
    protected <T> T sendError(Throwable throwable, ServerResponse response) {
        Throwable realCause = throwable;
        if (throwable instanceof CompletionException) {
            realCause = throwable.getCause();
        }
        response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
        response.send("Failed to process request: " + realCause.getClass().getName() + "(" + realCause.getMessage() + ")");
        LOGGER.log(Level.WARNING, "Failed to process request", throwable);
        return null;
    }

}
