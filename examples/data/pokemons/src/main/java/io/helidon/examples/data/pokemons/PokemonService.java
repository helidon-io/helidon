/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.data.pokemons;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.examples.data.pokemons.dao.PokemonRepository;
import io.helidon.examples.data.pokemons.dao.TypesRepository;
import io.helidon.examples.data.pokemons.model.Pokemon;
import io.helidon.reactive.webserver.*;

import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;

/**
 * Example service using a database.
 */
public class PokemonService implements Service {

    private static final Logger LOGGER = Logger.getLogger(PokemonService.class.getName());

    PokemonRepository pokemonDao;
    TypesRepository typesDao;

    PokemonService(EntityManager entityManager) {
        // TODO: Initialization - manual for SE/Pico, @Inject for MP
        this.pokemonDao = null;
        this.typesDao = null;
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
                // Get pokemons by Type name
                .get("/pokemon/type/{name}", this::getPokemonsByType)
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
        response.headers().contentType(MediaTypes.TEXT_PLAIN);
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
        response.send(
                StreamSupport.stream(typesDao.findAll().spliterator(), true)
                        // TODO: This needs to be done better
                        .map(it -> it.toJson()));
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
        response.send(
                StreamSupport.stream(pokemonDao.findAll().spliterator(), true)
                        .map(it -> it.toJson()));
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
            pokemonDao.findById(pokemonId)
                    .ifPresentOrElse(
                            it -> response.send(it.toJson()),
                            () -> response.send(JsonObject.NULL)
                    );
        } catch (Throwable t) {
            sendError(t, response);
        }
    }

    /**
     * Get a single pokemon by name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonByName(ServerRequest request, ServerResponse response) {
        try {
            String pokemonName = request.path().param("name");
            response.send(
                    pokemonDao.findByName(pokemonName).stream().map(it -> it.toJson()));
        } catch (Throwable t) {
            sendError(t, response);
        }
    }

    /**
     * Get a pokemons by type name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonsByType(ServerRequest request, ServerResponse response) {
        try {
            String typeName = request.path().param("name");
            response.send(
                    StreamSupport.stream(pokemonDao.pokemonsByType(typeName).spliterator(), true)
                            .map(it -> it.toJson()));
        } catch (Throwable t) {
            sendError(t, response);
        }
    }

    /**
     * Insert new pokemon with specified name.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void insertPokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        try {
            pokemonDao.save(pokemon);
            response.send("Inserted Pokemon\n");
        } catch (Throwable t) {
            sendError(t, response);
        }
    }

    /**
     * Update a pokemon.
     * Uses a transaction.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updatePokemon(ServerRequest request, ServerResponse response, Pokemon pokemon) {
        try {
            pokemonDao.update(pokemon);
            response.send("Updated Pokemon\n");
        } catch (Throwable t) {
            sendError(t, response);
        }
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
            pokemonDao.deleteById(id);
            response.send("Deleted Pokemon\n");
        } catch (NumberFormatException ex) {
            sendError(ex, response);
        }
    }

    /**
     * Delete all pokemons.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void deleteAllPokemons(ServerRequest request, ServerResponse response) {
        try {
            int id = Integer.parseInt(request.path().param("id"));
            pokemonDao.deleteAll();
            response.send("Deleted all pokemons\n");
        } catch (NumberFormatException ex) {
            sendError(ex, response);
        }
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
