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

import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.dbclient.DbClient;
import io.helidon.http.BadRequestException;
import io.helidon.http.NotFoundException;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

/**
 * Example service using a database.
 */
public class PokemonService implements HttpService {

    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of());
    private final DbClient dbClient;

    PokemonService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::index)
                // List all types
                .get("/type", this::listTypes)
                // List all Pokémon
                .get("/pokemon", this::listPokemons)
                // Get Pokémon by name
                .get("/pokemon/name/{name}", this::getPokemonByName)
                // Get Pokémon by ID
                .get("/pokemon/{id}", this::getPokemonById)
                // Create new Pokémon
                .post("/pokemon", Handler.create(Pokemon.class, this::insertPokemon))
                // Update name of existing Pokémon
                .put("/pokemon", Handler.create(Pokemon.class, this::updatePokemon))
                // Delete Pokémon by ID including type relation
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
        response.send("""
                Pokemon JDBC Example:
                     GET /type                - List all pokemon types
                     GET /pokemon             - List all pokemons
                     GET /pokemon/{id}        - Get pokemon by id
                     GET /pokemon/name/{name} - Get pokemon by name
                     POST /pokemon            - Insert new pokemon:
                                                {"id":<id>,"name":<name>,"type":<type>}
                     PUT /pokemon             - Update pokemon
                                                {"id":<id>,"name":<name>,"type":<type>}
                     DELETE /pokemon/{id}     - Delete pokemon with specified id
                """);
    }

    /**
     * Return JsonArray with all stored Pokémon.
     * Pokémon object contains list of all type names.
     * This method is abstract because implementation is DB dependent.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listTypes(ServerRequest request, ServerResponse response) {
        JsonArray jsonArray = dbClient.execute()
                .namedQuery("select-all-types")
                .map(row -> row.as(JsonObject.class))
                .collect(JSON_FACTORY::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll)
                .build();
        response.send(jsonArray);
    }

    /**
     * Return JsonArray with all stored Pokémon.
     * Pokémon object contains list of all type names.
     * This method is abstract because implementation is DB dependent.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listPokemons(ServerRequest request, ServerResponse response) {
        JsonArray jsonArray = dbClient.execute().namedQuery("select-all-pokemons")
                .map(row -> row.as(JsonObject.class))
                .collect(JSON_FACTORY::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::addAll)
                .build();
        response.send(jsonArray);
    }

    /**
     * Get a single Pokémon by id.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonById(ServerRequest request, ServerResponse response) {
        int pokemonId = Integer.parseInt(request.path()
                .pathParameters()
                .get("id"));

        response.send(dbClient.execute().createNamedGet("select-pokemon-by-id")
                .addParam("id", pokemonId)
                .execute()
                .orElseThrow(() -> new NotFoundException("Pokemon " + pokemonId + " not found"))
                .as(JsonObject.class));
    }

    /**
     * Get a single Pokémon by name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonByName(ServerRequest request, ServerResponse response) {
        String pokemonName = request.path().pathParameters().get("name");
        response.send(dbClient.execute().namedGet("select-pokemon-by-name", pokemonName)
                .orElseThrow(() -> new NotFoundException("Pokemon " + pokemonName + " not found"))
                .as(JsonObject.class));
    }

    /**
     * Insert new Pokémon with specified name.
     *
     * @param pokemon  request entity
     * @param response the server response
     */
    private void insertPokemon(Pokemon pokemon, ServerResponse response) {
        long count = dbClient.execute().createNamedInsert("insert-pokemon")
                .indexedParam(pokemon)
                .execute();
        response.send("Inserted: " + count + " values\n");
    }

    /**
     * Update a Pokémon.
     * Uses a transaction.
     *
     * @param pokemon  request entity
     * @param response the server response
     */
    private void updatePokemon(Pokemon pokemon, ServerResponse response) {
        long count = dbClient.execute().createNamedUpdate("update-pokemon-by-id")
                .namedParam(pokemon)
                .execute();
        response.send("Updated: " + count + " values\n");
    }

    /**
     * Delete Pokémon with specified id (key).
     *
     * @param request  the server request
     * @param response the server response
     */
    private void deletePokemonById(ServerRequest request, ServerResponse response) {
        int id = request.path()
                .pathParameters()
                .first("id").map(Integer::parseInt)
                .orElseThrow(() -> new BadRequestException("No pokemon id"));
        long count = dbClient.execute().createNamedDelete("delete-pokemon-by-id")
                .addParam("id", id)
                .execute();
        response.send("Deleted: " + count + " values\n");
    }
}
