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

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.data.HelidonData;
import io.helidon.examples.data.pokemons.model.Pokemon;
import io.helidon.examples.data.pokemons.repository.PokemonRepository;
import io.helidon.examples.data.pokemons.repository.TypeRepository;
import io.helidon.nima.webserver.http.*;

/**
 * Example Nima service using blocking CRUD data repoository.
 */
public class PokemonService implements HttpService {

    // Pokemon entity data repository
    private final PokemonRepository pokemonRepository;
    // Type entity data repository
    private final TypeRepository typeRepository;

    PokemonService() {
        // Initialize data repositories
        this.pokemonRepository = HelidonData.createRepository(PokemonRepository.class);
        this.typeRepository = HelidonData.createRepository(TypeRepository.class);
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/", this::index)
                // List all types
                .get("/type", this::listTypes)
                // List all pokemons
                .get("/pokemon", this::listPokemons)
                // Get pokemon by ID
                .get("/pokemon/{id}", this::getPokemonById)
                // Get pokemon by name
                .get("/pokemon/name/{name}", this::getPokemonByName)
                // Get pokemons by Type name
                .get("/pokemon/type/{name}", this::getPokemonsByType)
                // Create new pokemon
                .post("/pokemon", Handler.create(Pokemon.class, this::insertPokemon))
                // Update name of existing pokemon
                .put("/pokemon", Handler.create(Pokemon.class, this::updatePokemon))
                // Delete pokemon by ID including type relation
                .delete(Handler.create(this::deletePokemonById));
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
        + "     GET /pokemon/type/{name} - List all pokemons of given type\n"
        + "    POST /pokemon             - Insert new pokemon:\n"
        + "                                {\"id\":<id>,\"name\":<name>,\"type\":<type>}\n"
        + "     PUT /pokemon             - Update pokemon\n"
        + "                                {\"id\":<id>,\"name\":<name>,\"type\":<type>}\n"
        + "  DELETE /pokemon/{id}        - Delete pokemon with specified id\n");
    }

    /**
     * Return all stored Pokemon types.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listTypes(ServerRequest request, ServerResponse response) {
        // Iterable<E> findAll() is method added from CrudRepository interface
        response.send(typeRepository.findAll());
    }

    /**
     * Return all stored pokemons.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void listPokemons(ServerRequest request, ServerResponse response) {
        // Iterable<E> findAll() is method added from CrudRepository interface
        response.send(pokemonRepository.findAll());
    }

    /**
     * Get a single pokemon by id.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonById(ServerRequest request, ServerResponse response) {
            int pokemonId = Integer.parseInt(request.path().pathParameters().value("id"));
            // Optional<E> findById(ID id) is method added from CrudRepository interface
            pokemonRepository.findById(pokemonId)
                    .ifPresentOrElse(
                            it -> response.send(it),
                            () -> response.status(Http.Status.NOT_FOUND_404).send()
                    );
    }

    /**
     * Get a single pokemon by name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonByName(ServerRequest request, ServerResponse response) {
        String pokemonName = request.path().pathParameters().value("name");
        // Optional<Pokemon> findByName(String name) is method defined as query by method name
        pokemonRepository.findByName(pokemonName)
                .ifPresentOrElse(
                        it -> response.send(it),
                        () -> response.status(Http.Status.NOT_FOUND_404).send()
                );
    }

    /**
     * Get all pokemons of given type.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonsByType(ServerRequest request, ServerResponse response) {
        String typeName = request.path().pathParameters().value("name");
        // List<Pokemon> listByTypeName(String typeName) is method defined as query by method name
        response.send(pokemonRepository.listByTypeName(typeName));
    }

    /**
     * Get pokemon of given type and name.
     *
     * @param request  server request
     * @param response server response
     */
    private void getPokemonByTypeAndName(ServerRequest request, ServerResponse response) {
        String typeName = request.path().pathParameters().value("typeName");
        String pokemonName = request.path().pathParameters().value("pokemonName");
        // Optional<Pokemon> pokemonsByTypeAndName(String typeName, String pokemonName)
        // is method defined by custom query annotation
        pokemonRepository.pokemonsByTypeAndName(typeName, pokemonName)
                .ifPresentOrElse(
                        it -> response.send(it),
                        () -> response.status(Http.Status.NOT_FOUND_404).send()
                );
    }

    /**
     * Insert new pokemon.
     *
     * @param pokemon pokemon to insert
     */
    private Pokemon insertPokemon(Pokemon pokemon) {
        // <T extends E> T save(T entity) is method added from CrudRepository interface
        pokemonRepository.save(pokemon);
        return pokemon;
    }

    /**
     * Update a pokemon.
     *
     * @param pokemon pokemon to update
     */
    private Pokemon updatePokemon(Pokemon pokemon) {
        // <T extends E> T update(T entity) is method added from CrudRepository interface
        pokemonRepository.update(pokemon);
        return pokemon;
    }

    /**
     * Delete pokemon with specified id (key).
     *
     * @param request  the server request
     */
    private void deletePokemonById(ServerRequest request) {
        int id = Integer.parseInt(request.path().pathParameters().value("id"));
        // void deleteById(ID id) is method added from CrudRepository interface
        pokemonRepository.deleteById(id);
    }

}
