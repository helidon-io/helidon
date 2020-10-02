/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.simple.test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Stadium;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.simple.DbUtils;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify update operations on ORM.
 */
public class UpdateIT {
    
    private static PU pu;

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Create.dbInsertBrock(em);
            Create.dbInsertSaffron(em);
        });
    }

    @AfterAll
    public static void destroy() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Delete.dbDeleteBrock(em);
            Delete.dbDeleteSaffron(em);
            pu = null;
        });
    }

    /**
     * Update pokemon: evolve Broke's Geodude into Graveler.
     * Modification is done using entity instance.
     */
    @Test
    public void testUpdateEntity() {
        Pokemon[] pokemons = new Pokemon[1];
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            pokemons[0] = em.createQuery(
                    "SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                    .setParameter("name", "Geodude")
                    .getSingleResult();
            pokemons[0].getTypes().size();
            pokemons[0].setName("Graveler");
            pokemons[0].setCp(527);
            em.persist(pokemons[0]);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Pokemon dbGraveler = em.find(Pokemon.class, pokemons[0].getId());
            assertEquals(pokemons[0], dbGraveler);
        });
    }

    /**
     * Update pokemon: evolve Broke's Slowpoke into Slowbro.
     * Modification is done using JPQL.
     */
    @Test
    public void testUpdateJPQL() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            int updated = em.createQuery(
                    "UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.name = :name")
                    .setParameter("newName", "Slowbro")
                    .setParameter("newCp", 647)
                    .setParameter("name", "Slowpoke")
                    .executeUpdate();
            assertEquals(1, updated);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Pokemon dbWartortle = em.createQuery(
                    "SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                    .setParameter("name", "Slowbro")
                    .getSingleResult();
            assertEquals(647, dbWartortle.getCp());
        });
    }

    /**
     * Update pokemon: evolve Broke's Teddiursa into Ursaring.
     * Modification is done using CriteriaUpdate.
     */
    @Test
    public void testUpdateCriteria() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaUpdate<Pokemon> cu = cb.createCriteriaUpdate(Pokemon.class);
            Root<Pokemon> pokemonRoot = cu.from(Pokemon.class);
            cu.where(cb.equal(pokemonRoot.get("name"), "Teddiursa"))
                    .set("name", "Ursaring")
                    .set("cp", 1568);
            int updated = em.createQuery(cu).executeUpdate();
            assertEquals(1, updated);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Pokemon> cq = cb.createQuery(Pokemon.class);
            Root<Pokemon> pokemonRoot = cq.from(Pokemon.class);
            cq.select(pokemonRoot)
                    .where(cb.equal(pokemonRoot.get("name"), "Ursaring"));
            Pokemon dbUrsaring = em.createQuery(cq).getSingleResult();
            assertEquals(1568, dbUrsaring.getCp());
        });
    }

    /**
     * Update Saffron City data structure.
     * Replace stadium trainer with new guy who will get all pokemons from previous trainer.
     * Also Alakazam evolves to Mega Alakazam at the same time.
     */
    @Test
    public void testUpdateSaffron() {
        City[] cities = new City[1];
        Set<String> pokemonNames = new HashSet<>(6);
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            cities[0] = em.createQuery(
                    "SELECT c FROM City c WHERE c.name = :name", City.class)
                    .setParameter("name", "Saffron City")
                    .getSingleResult();
            Stadium stadium = cities[0].getStadium();
            Trainer sabrina = stadium.getTrainer();
            Trainer janine = new Trainer("Janine", 24);
            stadium.setTrainer(janine);
            List<Pokemon> pokemons = sabrina.getPokemons();
            janine.setPokemons(pokemons);
            sabrina.setPokemons(Collections.EMPTY_LIST);
            em.remove(sabrina);
            em.persist(janine);
            for (Pokemon pokemon : pokemons) {
                pokemon.setTrainer(janine);
                pokemonNames.add(pokemon.getName());
                em.persist(pokemon);
            }
            em.persist(stadium);
            Pokemon alkazam = DbUtils.findPokemonByName(pokemons, "Alakazam");
            System.out.println("TRAINER: "+alkazam.getTrainer().getName());
            // Update pokemon by query
            em.createQuery(
                    "UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.id = :id")
                    .setParameter("newName", "Mega Alakazam")
                    .setParameter("newCp", 4348)
                    .setParameter("id", alkazam.getId())
                    .executeUpdate();
            pokemonNames.remove("Alakazam");
            pokemonNames.add("Mega Alakazam");
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            City city = em.find(City.class, cities[0].getId());
            Stadium stadium = city.getStadium();
            Trainer trainer = stadium.getTrainer();
            em.refresh(trainer);
            List<Pokemon> pokemons = trainer.getPokemons();
            assertEquals(trainer.getName(), "Janine");
            for (Pokemon pokemon : pokemons) {
                assertTrue(pokemonNames.remove(pokemon.getName()), "Pokemon " + pokemon.getName() + " is missing");
            }
        });
    }

}
