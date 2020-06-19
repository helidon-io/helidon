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

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verify update operations on ORM.
 */
public class UpdateIT {
    
    private static PU pu;

    // Brock and his pokemons are used for update tests only
    private static void dbInsertBrock() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Create.dbInsertBrock(em);
        });
    }

    // Delete Brock and his pokemons after update tests
    private static void dbDeleteBrock() {
        pu.tx(pu -> {
            final EntityManager em = pu.getEm();
            Delete.dbDeleteBrock(em);
        });
    }

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
        dbInsertBrock();
    }

    @AfterAll
    public static void destroy() {
        dbDeleteBrock();
        pu = null;
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
    
    
}
