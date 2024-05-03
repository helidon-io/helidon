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
import io.helidon.tests.integration.jpa.common.model.Stadium;
import io.helidon.tests.integration.jpa.common.model.Trainer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actual implementation of {@link DeleteTest}.
 */
@Transactional
@ApplicationScoped
@SuppressWarnings({"SpellCheckingInspection"})
public class DeleteTestImpl extends AbstractTestImpl implements DeleteTest {

    @Override
    public void testDeleteEntity() {
        Pokemon orig = result(em
                .createQuery("SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                .setParameter("name", "Staryu"));
        assertThat(orig, is(not(nullValue())));
        em.remove(orig);

        clear();
        Pokemon actual = em.find(Pokemon.class, orig.getId());
        assertThat(actual, nullValue());
    }

    @Override
    public void testDeleteJPQL() {
        int deleted = em.createQuery("DELETE FROM Pokemon p WHERE p.name = :name")
                .setParameter("name", "Psyduck")
                .executeUpdate();
        assertThat(deleted, is(1));

        clear();
        List<Pokemon> results = em.createQuery("SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                .setParameter("name", "Psyduck")
                .getResultList();
        assertThat(results, is(empty()));
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public void testDeleteCriteria() {
        CriteriaBuilder cb1 = em.getCriteriaBuilder();
        CriteriaDelete<Pokemon> cd = cb1.createCriteriaDelete(Pokemon.class);
        Root<Pokemon> r1 = cd.from(Pokemon.class);
        cd.where(cb1.equal(r1.get("name"), "Corsola"));
        int deleted = em.createQuery(cd).executeUpdate();
        assertThat(deleted, is(1));

        clear();
        CriteriaBuilder cb2 = em.getCriteriaBuilder();
        CriteriaQuery<Pokemon> cq = cb2.createQuery(Pokemon.class);
        Root<Pokemon> r2 = cq.from(Pokemon.class);
        cq.select(r2).where(cb2.equal(r2.get("name"), "Corsola"));
        List<Pokemon> results = em.createQuery(cq).getResultList();
        assertThat(results, is(empty()));
    }

    @Override
    public void testDeleteCity() {
        City orig = result(em
                .createQuery("SELECT c FROM City c WHERE c.name = :name", City.class)
                .setParameter("name", "Viridian City"));
        assertThat(orig, is(not(nullValue())));
        Stadium stadium = orig.getStadium();
        Trainer trainer = stadium.getTrainer();
        List<Pokemon> pokemons = trainer.getPokemons();
        em.remove(orig);
        em.remove(trainer);
        pokemons.forEach(poklemon -> em.remove(poklemon));

        clear();
        City actual = result(em
                .createQuery("SELECT c FROM City c WHERE c.name = :name", City.class)
                .setParameter("name", "Viridian City"));
        assertThat(actual, is(nullValue()));
    }
}
