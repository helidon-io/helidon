/*
 * Copyright (city) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Stadium;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify delete operations of ORM.
 */
public class DeleteIT {

    private static PU pu;

    // Misty and her pokemons are used for delete tests only
    private static void dbInsertMisty() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Create.dbInsertMisty(em);
            Create.dbInsertViridian(em);
        });
    }

    // Delete Misty and her pokemons after delete tests
    private static void dbDeleteMisty() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Delete.dbDeleteMisty(em);
            Delete.dbDeleteViridian(em);
        });
    }

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
        dbInsertMisty();
    }

    @AfterAll
    public static void destroy() {
        dbDeleteMisty();
        pu = null;
    }

    /**
     * Delete pokemon: release Misty's Staryu.
     * Modification is done using entity instance.
     */
    @Test
    public void testDeleteEntity() {
        int ids[] = new int[1];
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Pokemon staryu = em.createQuery(
                    "SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                    .setParameter("name", "Staryu")
                    .getSingleResult();
            ids[0] = staryu.getId();
            em.remove(staryu);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Pokemon dbStaryu = em.find(Pokemon.class, ids[0]);
            assertNull(dbStaryu);
        });
    }

    /**
     * Delete pokemon: release Misty's Psyduck.
     * Modification is done using JPQL.
     */
    @Test
    public void testDeleteJPQL() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            int deleted = em.createQuery(
                    "DELETE FROM Pokemon p WHERE p.name = :name")
                    .setParameter("name", "Psyduck")
                    .executeUpdate();
            assertEquals(1, deleted);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            List<Pokemon> pokemons = em.createQuery(
                    "SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                    .setParameter("name", "Psyduck")
                    .getResultList();
            assertTrue(pokemons.isEmpty());
        });
    }

    /**
     * Delete pokemon: release Misty's Corsola.
     * Modification is done using CriteriaDelete.
     */
    @Test
    public void testDeleteCriteria() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaDelete<Pokemon> cu = cb.createCriteriaDelete(Pokemon.class);
            Root<Pokemon> pokemonRoot = cu.from(Pokemon.class);
            cu.where(cb.equal(pokemonRoot.get("name"), "Corsola"));
            int deleted = em.createQuery(cu).executeUpdate();
            assertEquals(1, deleted);
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Pokemon> cq = cb.createQuery(Pokemon.class);
            Root<Pokemon> pokemonRoot = cq.from(Pokemon.class);
            cq.select(pokemonRoot)
                    .where(cb.equal(pokemonRoot.get("name"), "Corsola"));
            List<Pokemon> pokemons = em.createQuery(cq).getResultList();
            assertTrue(pokemons.isEmpty());
        });
    }

    /**
     * Delete Viridian City.
     */
    @Test
    public void testDeleteViridianCity() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            City city = em.createQuery(
                    "SELECT c FROM City c WHERE c.name = :name", City.class)
                    .setParameter("name", "Viridian City")
                    .getSingleResult();
            Stadium stadium = city.getStadium();
            Trainer trainer = stadium.getTrainer();
            List<Pokemon> pokemons = trainer.getPokemons();
            em.remove(city);
            em.remove(trainer);
            pokemons.forEach(poklemon -> em.remove(poklemon));
        });
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            List<City> cities = em.createQuery(
                    "SELECT c FROM City c WHERE c.name = :name", City.class)
                    .setParameter("name", "Viridian City")
                    .getResultList();
            assertTrue(cities.isEmpty());
        });
    }

}
