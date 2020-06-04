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

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.transaction.Transaction;
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
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Class PokemonService.
 */
@Path("pokemon")
public class PokemonResource {

    @PersistenceContext(unitName = "test")
    private EntityManager entityManager;

    @Inject
    private Transaction transaction;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPokemons() {
        List<Pokemon> pokemons =  entityManager.createNamedQuery("getPokemons", Pokemon.class).getResultList();
        return Response.ok(new GenericEntity<>(pokemons){}).build();
    }

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

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional(Transactional.TxType.REQUIRED)
    public void deletePokemon(@PathParam("id") String id) {
        Pokemon pokemon = getPokemonById(id);
        entityManager.remove(pokemon);
    }

    @GET
    @Path("name/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pokemon getPokemonByName(@PathParam("name") String name) {
        TypedQuery<Pokemon> query = entityManager.createNamedQuery("getPokemonByName", Pokemon.class);
        List<Pokemon> list = query.setParameter("name", name).getResultList();
        if (list.size() == 0) {
            throw new NotFoundException("Unable to find pokemon with name " + name);
        }
        return list.get(0);
    }

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
