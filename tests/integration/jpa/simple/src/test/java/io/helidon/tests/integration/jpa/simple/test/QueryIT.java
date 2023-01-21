/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import io.helidon.tests.integration.jpa.model.City;
import io.helidon.tests.integration.jpa.model.Pokemon;
import io.helidon.tests.integration.jpa.model.Trainer;
import io.helidon.tests.integration.jpa.simple.DbUtils;
import io.helidon.tests.integration.jpa.simple.PU;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * Verify query operations of ORM.
 */
public class QueryIT {

    private static PU pu;

    @BeforeAll
    public static void setup() {
        pu = PU.getInstance();
        pu.tx(pu -> DbUtils.dbInit(pu));
    }

    @AfterAll
    public static void destroy() {
        pu.tx(pu -> DbUtils.dbCleanup(pu));
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
            assertThat(ash, notNullValue());
            assertThat(pokemons, not(empty()));
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
            assertThat(ash, notNullValue());
            assertThat(pokemons, not(empty()));
        });
    }

    /**
     * Query trainer Ash and his pokemons using CriteriaQuery.
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
            assertThat(ash, notNullValue());
            assertThat(pokemons, not(empty()));
        });
    }

    /**
     * Query Celadon city using JPQL.
     */
    @Test
    public void testQueryCeladonJPQL() {
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            City city = em.createQuery(
                            "SELECT c FROM City c "
                                    + "JOIN FETCH c.stadium s "
                                    + "JOIN FETCH s.trainer t "
                                    + "WHERE c.name = :name", City.class)
                    .setParameter("name", "Celadon City")
                    .getSingleResult();
            assertThat(city.getName(), is("Celadon City"));
            assertThat(city.getStadium().getName(), is("Celadon Gym"));
            assertThat(city.getStadium().getTrainer().getName(), is("Erika"));
        });
    }

    /**
     * Query Celadon city using CriteriaQuery.
     */
    @Test
    public void testQueryCeladonCriteria() {
        pu.tx(pu -> {
            final EntityManager em = pu.getCleanEm();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<City> cq = cb.createQuery(City.class);
            Root<City> cityRoot = cq.from(City.class);
            cityRoot
                    .fetch("stadium")
                    .fetch("trainer");
            cq.select(cityRoot)
                    .where(cb.equal(cityRoot.get("name"), "Celadon City"));
            City city = em.createQuery(cq).getSingleResult();
            assertThat(city.getName(), is("Celadon City"));
            assertThat(city.getStadium().getName(), is("Celadon Gym"));
            assertThat(city.getStadium().getTrainer().getName(), is("Erika"));
        });
    }

}
