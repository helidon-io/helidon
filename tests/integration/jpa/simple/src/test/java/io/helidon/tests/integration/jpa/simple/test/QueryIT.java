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

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.simple.DbUtils;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify query operations of ORM.
 */
public class QueryIT {
    
    private static PU pu;

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
    }

    @AfterAll
    public static void destroy() {
        pu = null;
    }

    /**
     * Find trainer Ash and his pokemons.
     */
    @Test
    public void testFind() {
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Trainer ash = em.find(Trainer.class, DbUtils.ASH_ID);
            List<Pokemon> pokemons = ash.getPokemons();
            assertNotNull(ash);
            assertFalse(pokemons.isEmpty());
        });
    }

    /**
     * Query trainer Ash and his pokemons using JPQL.
     */
    @Test
    public void testQueryJPQL() {
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            Trainer ash = em.createQuery(
                    "SELECT t FROM Trainer t JOIN FETCH t.pokemons p WHERE t.id = :id", Trainer.class)
                    .setParameter("id", DbUtils.ASH_ID)
                    .getSingleResult();
            List<Pokemon> pokemons = ash.getPokemons();
            assertNotNull(ash);
            assertFalse(pokemons.isEmpty());
        });
    }

    /**
     * Query trainer Ash and his pokemons using JPQL.
     */
    @Test
    public void testQueryCriteria() {
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Trainer> cq = cb.createQuery(Trainer.class);
            Root<Trainer> trainerRoot = cq.from(Trainer.class);
            cq.select(trainerRoot)
                    .where(cb.equal(trainerRoot.get("id"), DbUtils.ASH_ID));
            Trainer ash = em.createQuery(cq).getSingleResult();
            List<Pokemon> pokemons = ash.getPokemons();
            assertNotNull(ash);
            assertFalse(pokemons.isEmpty());
        });
    }

}