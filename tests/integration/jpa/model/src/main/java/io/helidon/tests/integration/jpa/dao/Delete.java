/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.dao;

import java.util.List;

import javax.persistence.EntityManager;

import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Stadium;
import io.helidon.tests.integration.jpa.model.Trainer;

/**
 * Delete data for the tests.
 */
public class Delete {

    private Delete() {
        throw new UnsupportedOperationException("Instances of Delete class are not allowed");
    }

    /**
     * Delete Brock and his pokemons.
     * Brock and his pokemons are used for update tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteBrock(final EntityManager em) {
        dbDeleteTrainerAndHisPokemons(em, "Brock");
    }

    /**
     * Delete Misty and her pokemons.
     * Misty and her pokemons are used for delete tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteMisty(final EntityManager em) {
        dbDeleteTrainerAndHisPokemons(em, "Misty");
    }

    /**
     * Delete Ash and his pokemons.
     * Ash and his pokemons are used for delete tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteAsh(final EntityManager em) {
        dbDeleteTrainerAndHisPokemons(em, "Ash Ketchum");
    }

    /**
     * Delete Celadon City.
     * Celadon City is used for query tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteCeladon(final EntityManager em) {
        dbDeleteCity(em, "Celadon City");
    }

    /**
     * Delete Saffron City.
     * Saffron City is used for update tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteSaffron(final EntityManager em) {
        dbDeleteCity(em, "Saffron City");
    }

    /**
     * Delete Viridian City.
     * Viridian City is used for update tests only.
     *
     * @param em Entity manager instance
     */
    public static void dbDeleteViridian(final EntityManager em) {
        dbDeleteCity(em, "Viridian City");
    }

    /**
     * Delete city.
     *
     * @param em Entity manager instance
     * @param name name of city to delete
     */
    public static void dbDeleteCity(final EntityManager em, final String name) {
        List<City> cities = em.createQuery(
                "SELECT c FROM City c WHERE c.name = :name", City.class)
                .setParameter("name", name)
                .getResultList();
        if (!cities.isEmpty()) {
            cities.forEach((city) -> {
                Stadium stadium = city.getStadium();
                Trainer trainer = stadium.getTrainer();
                List<Pokemon> pokemons = trainer.getPokemons();
                em.remove(city);
                //em.remove(stadium);
                pokemons.forEach((pokemon) -> em.remove(pokemon));
                em.remove(trainer);
            });
        }
    }

    /**
     * Delete trainer and his pokemons.
     * Trainer is identified by his name.
     *
     * @param em Entity manager instance
     * @param name name of trainer to delete
     */
    public static void dbDeleteTrainerAndHisPokemons(final EntityManager em, final String name) {
        List<Trainer> trainers = em.createQuery(
                "SELECT t FROM Trainer t WHERE t.name = :name", Trainer.class)
                .setParameter("name", name)
                .getResultList();
        if (!trainers.isEmpty()) {
            trainers.forEach((trainer) -> {
                List<Pokemon> pokemons = em.createQuery(
                        "SELECT p FROM Pokemon p INNER JOIN p.trainer t WHERE t.name = :name", Pokemon.class)
                        .setParameter("name", name)
                        .getResultList();
                pokemons.forEach((pokemon) -> em.remove(pokemon));
                em.remove(trainer);
            });
        }
    }

    /**
     * Delete all pokemons.
     *
     * @param em Entity manager instance
     */
    public static void deletePokemons(final EntityManager em) {
        em.createQuery("DELETE FROM Pokemon").executeUpdate();
    }

    /**
     * Delete all trainers.
     *
     * @param em Entity manager instance
     */
    public static void deleteTrainers(final EntityManager em) {
        em.createQuery("DELETE FROM Trainer").executeUpdate();
    }

    /**
     * Delete all types.
     *
     * @param em Entity manager instance
     */
    public static void deleteTypes(final EntityManager em) {
        em.createQuery("DELETE FROM Type").executeUpdate();
    }

    /**
     * Delete all cities.
     *
     * @param em Entity manager instance
     */
    public static void deleteCities(final EntityManager em) {
        em.createQuery("DELETE FROM City").executeUpdate();
    }

    /**
     * Delete all stadiums.
     *
     * @param em Entity manager instance
     */
    public static void deleteStadiums(final EntityManager em) {
        em.createQuery("DELETE FROM Stadium").executeUpdate();
    }

    /**
     * Delete all database records.
     *
     * @param em Entity manager instance
     */
    public static void dbCleanup(final EntityManager em) {
        deleteCities(em);
        deleteStadiums(em);
        deletePokemons(em);
        deleteTrainers(em);
        deleteTypes(em);
    }

}
