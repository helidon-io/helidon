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
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.dao.Create;
import io.helidon.tests.integration.jpa.dao.Delete;
import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Stadium;
import io.helidon.tests.integration.jpa.model.Trainer;

/**
 * Verify delete operations of ORM (server side).
 */
@ApplicationScoped
public class DeleteIT {

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
        Create.dbInsertMisty(em);
        Create.dbInsertViridian(em);
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
        Delete.dbDeleteMisty(em);
        Delete.dbDeleteViridian(em);
        return result;
    }

    /**
     * Delete pokemon: release Misty's Staryu.
     * Modification is done using entity instance.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testDeleteEntity(TestResult result) {
        Pokemon staryu = em.createQuery(
                "SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                .setParameter("name", "Staryu")
                .getSingleResult();
        int id = staryu.getId();
        em.remove(staryu);
        DbUtils.cleanEm(em);
        Pokemon dbStaryu = em.find(Pokemon.class, id);
        result.assertNull(dbStaryu);
        return result;
    }

    /**
     * Delete pokemon: release Misty's Psyduck.
     * Modification is done using JPQL.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testDeleteJPQL(TestResult result) {
        int deleted = em.createQuery(
                "DELETE FROM Pokemon p WHERE p.name = :name")
                .setParameter("name", "Psyduck")
                .executeUpdate();
        result.assertEquals(1, deleted);
        DbUtils.cleanEm(em);
        List<Pokemon> pokemons = em.createQuery(
                "SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                .setParameter("name", "Psyduck")
                .getResultList();
        result.assertTrue(pokemons.isEmpty());
        return result;
    }

    /**
     * Delete pokemon: release Misty's Corsola.
     * Modification is done using CriteriaDelete.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testDeleteCriteria(TestResult result) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<Pokemon> cu = cb.createCriteriaDelete(Pokemon.class);
        Root<Pokemon> pokemonRoot = cu.from(Pokemon.class);
        cu.where(cb.equal(pokemonRoot.get("name"), "Corsola"));
        int deleted = em.createQuery(cu).executeUpdate();
        result.assertEquals(1, deleted);
        DbUtils.cleanEm(em);
        cb = em.getCriteriaBuilder();
        CriteriaQuery<Pokemon> cq = cb.createQuery(Pokemon.class);
        pokemonRoot = cq.from(Pokemon.class);
        cq.select(pokemonRoot)
                .where(cb.equal(pokemonRoot.get("name"), "Corsola"));
        List<Pokemon> pokemons = em.createQuery(cq).getResultList();
        result.assertTrue(pokemons.isEmpty());
        return result;
    }

    /**
     * Delete Viridian City.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testDeleteViridianCity(TestResult result) {
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
        DbUtils.cleanEm(em);
        List<City> cities = em.createQuery(
                "SELECT c FROM City c WHERE c.name = :name", City.class)
                .setParameter("name", "Viridian City")
                .getResultList();
        result.assertTrue(cities.isEmpty());
        return result;
    }

}
