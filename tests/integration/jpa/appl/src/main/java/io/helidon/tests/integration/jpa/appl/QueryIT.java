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
package io.helidon.tests.integration.jpa.appl;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;

/**
 * Verify query operations of ORM (server side).
 */
@ApplicationScoped
public class QueryIT {

    private static int ASH_ID;

    @PersistenceContext(unitName = "test")
    private EntityManager em;
    
    /**
     * Initialize test suite.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult setup(TestResult result) {
        ASH_ID = Create.dbInsertAsh(em);
        return result;
    }

    /**
     * Clean up test suite.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult destroy(TestResult result) {
        Delete.dbDeleteAsh(em);
        return result;
    }

    /**
     * Find trainer Ash and his pokemons.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testFind(TestResult result) {
        Trainer ash = em.find(Trainer.class, ASH_ID);
        List<Pokemon> pokemons = ash.getPokemons();
        result.assertNotNull(ash);
        result.assertFalse(pokemons.isEmpty());
        return result;
    }

    /**
     * Query trainer Ash and his pokemons using JPQL.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testQueryJPQL(TestResult result) {
        Trainer ash = em.createQuery(
                "SELECT t FROM Trainer t JOIN FETCH t.pokemons p WHERE t.id = :id", Trainer.class)
                .setParameter("id", ASH_ID)
                .getSingleResult();
        List<Pokemon> pokemons = ash.getPokemons();
        result.assertNotNull(ash);
        result.assertFalse(pokemons.isEmpty());
        return result;
    }

    /**
     * Query trainer Ash and his pokemons using CriteriaQuery.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testQueryCriteria(TestResult result) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Trainer> cq = cb.createQuery(Trainer.class);
        Root<Trainer> trainerRoot = cq.from(Trainer.class);
        cq.select(trainerRoot)
                .where(cb.equal(trainerRoot.get("id"), ASH_ID));
        Trainer ash = em.createQuery(cq).getSingleResult();
        List<Pokemon> pokemons = ash.getPokemons();
        result.assertNotNull(ash);
        result.assertFalse(pokemons.isEmpty());
        return result;
    }

}
