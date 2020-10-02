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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

/**
 * Verify update operations of ORM (server side).
 */
@ApplicationScoped
public class UpdateIT {

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
        Create.dbInsertBrock(em);
        Create.dbInsertSaffron(em);
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
        Delete.dbDeleteBrock(em);
        Delete.dbDeleteSaffron(em);
        return result;
    }

    /**
     * Update pokemon: evolve Broke's Geodude into Graveler.
     * Modification is done using entity instance.
     *
     * @param result test execution result
     * @return test execution result
    */
    @MPTest
    public TestResult testUpdateEntity(TestResult result) {
        Pokemon[] pokemons = new Pokemon[1];
        pokemons[0] = em.createQuery(
                "SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                .setParameter("name", "Geodude")
                .getSingleResult();
        pokemons[0].getTypes().size();
        pokemons[0].setName("Graveler");
        pokemons[0].setCp(527);
        em.persist(pokemons[0]);
        DbUtils.cleanEm(em);
        Pokemon dbGraveler = em.find(Pokemon.class, pokemons[0].getId());
        result.assertEquals(pokemons[0], dbGraveler);
        return result;
    }

    /**
     * Update pokemon: evolve Broke's Slowpoke into Slowbro.
     * Modification is done using JPQL.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testUpdateJPQL(TestResult result) {
        int updated = em.createQuery(
                "UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.name = :name")
                .setParameter("newName", "Slowbro")
                .setParameter("newCp", 647)
                .setParameter("name", "Slowpoke")
                .executeUpdate();
        result.assertEquals(1, updated);
        DbUtils.cleanEm(em);
        Pokemon dbWartortle = em.createQuery(
                "SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                .setParameter("name", "Slowbro")
                .getSingleResult();
        result.assertEquals(647, dbWartortle.getCp());
        return result;
    }

    /**
     * Update pokemon: evolve Broke's Teddiursa into Ursaring.
     * Modification is done using CriteriaUpdate.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testUpdateCriteria(TestResult result) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<Pokemon> cu = cb.createCriteriaUpdate(Pokemon.class);
        Root<Pokemon> pokemonRoot = cu.from(Pokemon.class);
        cu.where(cb.equal(pokemonRoot.get("name"), "Teddiursa"))
                .set("name", "Ursaring")
                .set("cp", 1568);
        int updated = em.createQuery(cu).executeUpdate();
        result.assertEquals(1, updated);
        DbUtils.cleanEm(em);
        cb = em.getCriteriaBuilder();
        CriteriaQuery<Pokemon> cq = cb.createQuery(Pokemon.class);
        pokemonRoot = cq.from(Pokemon.class);
        cq.select(pokemonRoot)
                .where(cb.equal(pokemonRoot.get("name"), "Ursaring"));
        Pokemon dbUrsaring = em.createQuery(cq).getSingleResult();
        result.assertEquals(1568, dbUrsaring.getCp());
        return result;
    }

    /**
     * Update Saffron City data structure.
     * Replace stadium trainer with new guy who will get all pokemons from previous trainer.
     * Also Alakazam evolves to Mega Alakazam at the same time.
     *
     * @param result test execution result
     * @return test execution result
     */
    @MPTest
    public TestResult testUpdateSaffron(TestResult result) {
        City[] cities = new City[1];
        Set<String> pokemonNames = new HashSet<>(6);
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
        // Update pokemon by query
        em.createQuery(
                "UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.id = :id")
                .setParameter("newName", "Mega Alakazam")
                .setParameter("newCp", 4348)
                .setParameter("id", alkazam.getId())
                .executeUpdate();
        pokemonNames.remove("Alakazam");
        pokemonNames.add("Mega Alakazam");
        DbUtils.cleanEm(em);
        City city = em.find(City.class, cities[0].getId());
        stadium = city.getStadium();
        Trainer trainer = stadium.getTrainer();
        em.refresh(trainer);
        pokemons = trainer.getPokemons();
        result.assertEquals(trainer.getName(), "Janine");
        for (Pokemon pokemon : pokemons) {
            result.assertTrue(pokemonNames.remove(pokemon.getName()), "Pokemon " + pokemon.getName() + " is missing");
        }
        return result;
    }

}
