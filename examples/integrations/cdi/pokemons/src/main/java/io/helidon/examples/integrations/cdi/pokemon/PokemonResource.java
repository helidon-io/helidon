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
package io.helidon.examples.integrations.cdi.pokemon;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * This class implements REST endpoints to interact with Pokemons. The following
 * operations are supported:
 *
 * GET /pokemon: Retrieve list of all pokemons
 * GET /pokemon/{id}: Retrieve single pokemon by ID
 * GET /pokemon/name/{name}: Retrieve single pokemon by name
 * DELETE /pokemon/{id}: Delete a pokemon by ID
 * POST /pokemon: Create a new pokemon
 */
@Path("pokemon")
public class PokemonResource {

    @PersistenceContext(unitName = "test")
    private EntityManager entityManager;

    /**
     * Retrieves list of all pokemons.
     *
     * @return List of pokemons.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Pokemon> getPokemons() {
        return entityManager.createNamedQuery("getPokemons", Pokemon.class).getResultList();
    }

    /**
     * Retrieves single pokemon by ID.
     *
     * @param id The ID.
     * @return A pokemon that matches the ID.
     * @throws NotFoundException If no pokemon found for the ID.
     */
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pokemon getPokemonById(@PathParam("id") String id) {
        try {
            return entityManager.find(Pokemon.class, Integer.valueOf(id));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unable to find pokemon with ID " + id);
        }
    }

    /**
     * Deletes a single pokemon by ID.
     *
     * @param id The ID.
     */
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(Transactional.TxType.REQUIRED)
    public void deletePokemon(@PathParam("id") String id) {
        Pokemon pokemon = getPokemonById(id);
        entityManager.remove(pokemon);
    }

    /**
     * Retrieves a pokemon by name.
     *
     * @param name The name.
     * @return A pokemon that matches the name.
     * @throws NotFoundException If no pokemon found for the name.
     */
    @GET
    @Path("name/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pokemon getPokemonByName(@PathParam("name") String name) {
        TypedQuery<Pokemon> query = entityManager.createNamedQuery("getPokemonByName", Pokemon.class);
        List<Pokemon> list = query.setParameter("name", name).getResultList();
        if (list.isEmpty()) {
            throw new NotFoundException("Unable to find pokemon with name " + name);
        }
        return list.get(0);
    }

    /**
     * Creates a new pokemon.
     *
     * @param pokemon New pokemon.
     * @throws BadRequestException If a problem was found.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional(Transactional.TxType.REQUIRED)
    public void createPokemon(Pokemon pokemon) {
        try {
            PokemonType pokemonType = entityManager.createNamedQuery("getPokemonTypeById", PokemonType.class)
                    .setParameter("id", pokemon.getType()).getSingleResult();
            pokemon.setPokemonType(pokemonType);
            entityManager.persist(pokemon);
        } catch (Exception e) {
            throw new BadRequestException("Unable to create pokemon with ID " + pokemon.getId());
        }
    }
}

