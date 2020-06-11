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

package io.helidon.examples.dbclient.pokemons2;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;;

import io.helidon.common.http.Http;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * This class implements REST endpoints to interact with Pokemon and Pokemon types.
 * The following operations are supported:
 *
 * GET /type: List all pokemon types
 * GET /pokemon: Retrieve list of all pokemons
 * GET /pokemon/{id}: Retrieve single pokemon by ID
 * GET /pokemon/name/{name}: Retrieve single pokemon by name
 * DELETE /pokemon/{id}: Delete a pokemon by ID
 * POST /pokemon: Create a new pokemon
 */
public class PokemonService implements Service {

    private static final Logger LOGGER = Logger.getLogger(PokemonService.class.getName());

    private final DbClient dbClient;

    PokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/type", this::listTypes)
                .get("/pokemon", this::listPokemons)
                .get("/pokemon/name/{name}", this::getPokemonByName)
                .get("/pokemon/{id}", this::getPokemonById)
                .post("/pokemon", Handler.create(Pokemon.class, this::insertPokemon))
                .delete("/pokemon/{id}", this::deletePokemonById);
    }

    private void listTypes(ServerRequest request, ServerResponse response) {
        try {
            List<PokemonType> pokemonTypes =
                    dbClient.execute(exec -> exec.namedQuery("select-all-types"))
                            .map(row -> row.as(PokemonType.class)).collectList().get();
            response.send(pokemonTypes);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void listPokemons(ServerRequest request, ServerResponse response) {
        try {
            List<Pokemon> pokemons =
                    dbClient.execute(exec -> exec.namedQuery("select-all-pokemons"))
                            .map(it -> it.as(Pokemon.class)).collectList().get();
            response.send(pokemons);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

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

    private void getPokemonByName(ServerRequest request, ServerResponse response) {
        String pokemonName = request.path().param("name");
        dbClient.execute(exec -> exec.namedGet("select-pokemon-by-name", pokemonName))
                .thenAccept(it -> {
                    if (it.isEmpty()) {
                        sendNotFound(response, "Pokemon " + pokemonName + " not found");
                    } else {
                        sendRow(it.get(), response);
                    }
                })
                .exceptionally(throwable -> sendError(throwable, response));
    }

    private void insertPokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        dbClient.execute(exec -> exec
                .createNamedInsert("insert-pokemon")
                .indexedParam(pokemon)
                .execute())
                .thenAccept(count -> response.send("Inserted: " + count + " values\n"))
                .exceptionally(throwable -> sendError(throwable, response));
    }

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

    private void sendNotFound(ServerResponse response, String message) {
        response.status(Http.Status.NOT_FOUND_404);
        response.send(message);
    }

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
     * with {@link java.util.concurrent.CompletionStage#exceptionally(java.util.function.Function)}
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
