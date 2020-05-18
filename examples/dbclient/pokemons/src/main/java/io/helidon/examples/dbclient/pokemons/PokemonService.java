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

package io.helidon.examples.dbclient.pokemons;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Example service using a database.
 */
public class PokemonService implements Service {

    private static final Logger LOGGER = Logger.getLogger(PokemonService.class.getName());

    private final DbClient dbClient;

    PokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                .get("/", this::index)
                // List all types
                .get("/type", this::listTypes)
                // List all pokemons
                .get("/pokemon", this::listPokemons)
                // Get pokemon by name
                .get("/pokemon/name/{name}", this::getPokemonByName)
                // Get pokemon by ID
                .get("/pokemon/{id}", this::getPokemonById)
                // Create new pokemon
                .post("/pokemon", Handler.create(Pokemon.class, this::insertPokemon))
                // Update name of existing pokemon
                .put("/pokemon", Handler.create(Pokemon.class, this::updatePokemon))
                // Delete pokemon by ID including type relation
                .delete("/pokemon/{id}", this::deletePokemonById);
    }


    /**
     * Return index page.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void index(ServerRequest request, ServerResponse response) {
        response.send("Pokemon JDBC Example:\n"
        + "     GET /type                - List all pokemon types\n"
        + "     GET /pokemon             - List all pokemons\n"
        + "     GET /pokemon/{id}        - Get pokemon by id\n"
        + "     GET /pokemon/name/{name} - Get pokemon by name\n"
        + "    POST /pokemon             - Insert new pokemon:\n"
        + "                                {\"id\":<id>,\"name\":<name>,\"type\":<type>}\n"
        + "     PUT /pokemon             - Update pokemon\n"
        + "                                {\"id\":<id>,\"name\":<name>,\"type\":<type>}\n"
        + "  DELETE /pokemon/{id}        - Delete pokemon with specified id\n");
    }

    /**
     * Return JsonArray with all stored pokemons.
     * Pokemon object contains list of all type names.
     * This method is abstract because implementation is DB dependent.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listTypes(ServerRequest request, ServerResponse response) {
        dbClient.execute(exec -> exec.namedQuery("select-all-types"))
                .thenApply(it -> it.map(JsonObject.class))
                .thenApply(DbRows::publisher)
                .thenAccept(it -> response.send(it, JsonObject.class))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Return JsonArray with all stored pokemons.
     * Pokemon object contains list of all type names.
     * This method is abstract because implementation is DB dependent.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listPokemons(ServerRequest request, ServerResponse response) {
        dbClient.execute(exec -> exec.namedQuery("select-all-pokemons"))
                .thenApply(it -> it.map(JsonObject.class))
                .thenApply(DbRows::publisher)
                .thenAccept(it -> response.send(it, JsonObject.class))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Get a single pokemon by id.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonById(ServerRequest request, ServerResponse response) {
        try {
            int pokemonId = Integer.parseInt(request.path().param("id"));
            dbClient.execute(exec -> exec
                    .createNamedGet("select-pokemon-by-id")
                    .addParam("id", pokemonId)
                    .execute())
                    .thenAccept(maybeRow -> maybeRow
                    .ifPresentOrElse(
                            row -> sendRow(row, response),
                            () -> sendNotFound(response, "Pokemon " + pokemonId + " not found")))
                    .exceptionally(throwable -> sendError(throwable, response));
        } catch (NumberFormatException ex) {
            sendError(ex, response);
        }
    }

    /**
     * Get a single pokemon by name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonByName(ServerRequest request, ServerResponse response) {
        String pokemonName = request.path().param("name");
        dbClient.execute(exec -> exec.namedGet("select-pokemon-by-name", pokemonName))
                .onEmpty(() -> sendNotFound(response, "Pokemon " + pokemonName + " not found"))
                .onValue(row -> sendRow(row, response))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Insert new pokemon with specified name.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void insertPokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        dbClient.execute(exec -> exec
                .createNamedInsert("insert-pokemon")
                .indexedParam(pokemon)
                .execute())
                .thenAccept(count -> response.send("Inserted: " + count + " values\n"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Update a pokemon.
     * Uses a transaction.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updatePokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        dbClient.execute(exec -> exec
                .createNamedUpdate("update-pokemon-by-id")
                .namedParam(pokemon)
                .execute())
                .thenAccept(count -> response.send("Updated: " + count + " values\n"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Delete pokemon with specified id (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    private void deletePokemonById(ServerRequest request, ServerResponse response) {
        try {
            int id = Integer.parseInt(request.path().param("id"));
            dbClient.execute(exec -> exec
                    .createNamedDelete("delete-pokemon-by-id")
                    .addParam("id", id)
                    .execute())
                    .thenAccept(count -> response.send("Deleted: " + count + " values\n"))
                    .exceptionally(throwable -> sendError(throwable, response));
        } catch (NumberFormatException ex) {
            sendError(ex, response);
        }
    }

    /**
     * Delete pokemon with specified id (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    private void deleteAllPokemons(ServerRequest request, ServerResponse response) {
        // Response message contains information about deleted records from both tables
        StringBuilder sb = new StringBuilder();
        // Pokemon must be removed from both PokemonTypes and Pokemons tables in transaction
        dbClient.execute(exec -> exec
                // Execute delete from PokemonTypes table
                .createDelete("DELETE FROM Pokemons")
                .execute())
                // Process response when transaction is completed
                .thenAccept(count -> response.send("Deleted: " + count + " values\n"))
                .exceptionally(throwable -> sendError(throwable, response));
     }

    /**
     * Send a 404 status code.
     *
     * @param response the server response
     * @param message entity content
     */
    private void sendNotFound(ServerResponse response, String message) {
        response.status(Http.Status.NOT_FOUND_404);
        response.send(message);
    }

    /**
     * Send a single DB row as JSON object.
     *
     * @param row row as read from the database
     * @param response server response
     */
    private void sendRow(DbRow row, ServerResponse response) {
        response.send(row.as(javax.json.JsonObject.class));
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
    private <T> T sendError(Throwable throwable, ServerResponse response) {
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
