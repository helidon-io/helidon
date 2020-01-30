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

package io.helidon.examples.dbclient.pokemons.mongo;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.examples.dbclient.common.Pokemon;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * A simple service to greet you. Examples:
 *
 * The message is returned as a JSON object
 */
public class PokemonService implements Service {

    private static final Logger LOGGER = Logger.getLogger(PokemonService.class.getName());

    private static final String DELETE_ALL_POKETYPES
            = "{\"collection\":\"pokemon_types\",\"operation\":\"delete\",\"query\": {}}";
    private static final String DELETE_ALL_POKEMONS
            = "{\"collection\":\"pokemons\",\"operation\":\"delete\",\"query\": {}}";

    private final DbClient dbClient;

    PokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules
                // List all pokemons
                .get("/", this::listPokemons)
                // Get pokemon by name
                .get("/name/{name}", this::getPokemonByName)
                // Get pokemon by ID
                .get("/id/{id}", this::getPokemonById)
                // Create new pokemon
                .post("/pokemon", Handler.create(Pokemon.class, this::insertPokemon))
                // Add pokemon type
                .post("/type/{pokemonId}/{typeId}", this::insertPokemonType)
                // Update name of existing pokemon
                .put("/pokemon/{id}/{name}", this::updatePokemonName)
                // Delete all pokemons
                .delete("/", this::deleteAllPokemons)
                // Delete pokemon by ID including type relation
                .delete("/id/{id}", this::deletePokemonById);
    }

    /**
     * Return JsonArray with all stored pokemons.
     * Pokemon object contains list of all type names.
     * This method is abstract because implementation is DB dependent.
     *
     * @param request  the server request
     * @param response the server response
     */
    protected void listPokemons(ServerRequest request, ServerResponse response) {
        dbClient.execute(exec -> exec.namedQuery("select-all-pokemons"))
                .thenAccept(response::send)
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
                .thenAccept(count -> response.send("Inserted: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Insert new pokemon type to pokemon with specified id.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void insertPokemonType(ServerRequest request, ServerResponse response) {
        try {
            int pokemonId = Integer.parseInt(request.path().param("pokemonId"));
            int typeId = Integer.parseInt(request.path().param("typeId"));
            dbClient.execute(exec -> exec
                    .namedInsert("insert-poketype", pokemonId, typeId))
                    .thenAccept(count -> response.send("Inserted: " + count + " values"))
                    .exceptionally(throwable -> sendError(throwable, response));
        } catch (NumberFormatException ex) {
            sendError(ex, response);
        }
    }

    /**
     * Update a pokemon.
     * Uses a transaction.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updatePokemonName(ServerRequest request, ServerResponse response) {
        int id = Integer.parseInt(request.path().param("id"));
        final String name = request.path().param("name");

        dbClient.execute(exec -> exec
                .createNamedUpdate("update-pokemon-name-by-id")
                .addParam("name", name)
                .addParam("id", id)
                .execute())
                .thenAccept(count -> response.send("Updated: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

    /**
     * Delete pokemon with specified id (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    protected void deletePokemonById(ServerRequest request, ServerResponse response) {
        int id = Integer.parseInt(request.path().param("id"));
        // Response message contains information about deleted records from both tables
        StringBuilder sb = new StringBuilder();
        // Pokemon must be removed from both PokemonTypes and Pokemons tables in transaction
        dbClient.execute(exec -> exec
                // Execute delete from PokemonTypes table
                .createNamedDelete("delete-poketype-by-id-pokemon")
                .addParam("id", id)
                .execute()
                .thenCompose(typesCount -> {
                    sb.append("Deleted ").append(typesCount).append(" types");
                    // Execute delete from Pokemons table
                    return exec.createNamedDelete("delete-pokemon-by-id")
                            .addParam("id", id)
                            .execute();
                }))
                // Process response when transaction is completed
                .thenAccept(pokemonsCount -> {
                    sb.append(" and ").append(pokemonsCount).append(" pokemons");
                    response.send(sb.toString());
                })
                .exceptionally(throwable -> sendError(throwable, response));
     }

    /**
     * Delete pokemon with specified id (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    protected void deleteAllPokemons(ServerRequest request, ServerResponse response) {
        // Response message contains information about deleted records from both tables
        StringBuilder sb = new StringBuilder();
        // Pokemon must be removed from both PokemonTypes and Pokemons tables in transaction
        dbClient.execute(exec -> exec
                // Execute delete from PokemonTypes table
                .createDelete(DELETE_ALL_POKETYPES)
                .execute()
                .thenCompose(typesCount -> {
                    sb.append("Deleted ").append(typesCount).append(" types");
                    // Execute delete from Pokemons table
                    return exec.createDelete(DELETE_ALL_POKEMONS)
                            .execute();
                }))
                // Process response when transaction is completed
                .thenAccept(pokemonsCount -> {
                    sb.append(" and ").append(pokemonsCount).append(" pokemons");
                    response.send(sb.toString());
                })
                .exceptionally(throwable -> sendError(throwable, response));
     }

    protected void listPokemonsWithTypes(ServerRequest request, ServerResponse response) {
        throw new UnsupportedOperationException("Not supported yet.");
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
