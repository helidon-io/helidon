/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.common;

import java.util.List;

import io.helidon.tests.integration.jpa.common.model.City;
import io.helidon.tests.integration.jpa.common.model.Pokemon;
import io.helidon.tests.integration.jpa.common.model.Trainer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Actual implementation of {@link QueryTest}.
 */
@Transactional
@ApplicationScoped
@SuppressWarnings("SpellCheckingInspection")
public class QueryTestImpl extends AbstractTestImpl implements QueryTest {

    @Override
    public void testFind() {
        clear();
        int id = dataSet.ash().getId();
        Trainer ash = em.find(Trainer.class, id);
        List<Pokemon> pokemons = ash.getPokemons();
        assertThat(ash, is(not(nullValue())));
        assertThat(pokemons, not(empty()));
    }

    @Override
    public void testQueryJPQL() {
        clear();
        int id = dataSet.ash().getId();
        Trainer trainer = result(em
                .createQuery("SELECT t FROM Trainer t JOIN FETCH t.pokemons p WHERE t.id = :id", Trainer.class)
                .setParameter("id", id));
        assertThat(trainer, is(not(nullValue())));
        List<Pokemon> pokemons = trainer.getPokemons(); // lazy fetch
        assertThat(pokemons, is(not(empty())));
    }

    @Override
    public void testQueryCriteria() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Trainer> query = cb.createQuery(Trainer.class);
        Root<Trainer> root = query.from(Trainer.class);
        query.select(root).where(cb.equal(root.get("name"), "Ash Ketchum"));
        Trainer trainer = result(em.createQuery(query));
        assertThat(trainer, is(not(nullValue())));
        assertThat(trainer.getPokemons(), is(not(empty())));
    }

    @Override
    public void testQueryCeladonJPQL() {
        City city = result(em
                .createQuery("SELECT c FROM City c "
                             + "JOIN FETCH c.stadium s "
                             + "JOIN FETCH s.trainer t "
                             + "WHERE c.name = :name", City.class)
                .setParameter("name", "Celadon City"));
        assertCeladon(city);
    }

    @Override
    public void testQueryCeladonCriteria() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<City> query = cb.createQuery(City.class);
        Root<City> root = query.from(City.class);
        root.fetch("stadium").fetch("trainer");
        query.select(root).where(cb.equal(root.get("name"), "Celadon City"));

        City city = result(em.createQuery(query));
        assertCeladon(city);
    }

    private void assertCeladon(City city) {
        assertThat(city, is(not(nullValue())));
        assertThat(city.getStadium(), is(not(nullValue())));
        assertThat(city.getStadium().getName(), is("Celadon Gym"));
        assertThat(city.getStadium().getTrainer(), is(not(nullValue())));
        assertThat(city.getStadium().getTrainer().getName(), is("Erika"));
    }
}
