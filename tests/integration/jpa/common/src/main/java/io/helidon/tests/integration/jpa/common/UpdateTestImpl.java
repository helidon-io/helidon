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

import java.util.ArrayList;
import java.util.List;

import io.helidon.tests.integration.jpa.common.model.City;
import io.helidon.tests.integration.jpa.common.model.Pokemon;
import io.helidon.tests.integration.jpa.common.model.Stadium;
import io.helidon.tests.integration.jpa.common.model.Trainer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actual implementation of {@link UpdateTest}.
 */
@Transactional
@ApplicationScoped
@SuppressWarnings({"SpellCheckingInspection", "ResultOfMethodCallIgnored"})
public class UpdateTestImpl extends AbstractTestImpl implements UpdateTest {

    @Override
    public void testUpdateEntity() {
        Pokemon orig = result(em
                .createQuery("SELECT p FROM Pokemon p WHERE p.name = :name", Pokemon.class)
                .setParameter("name", "Geodude"));
        orig.getTypes().size(); // lazy fetch
        orig.setName("Graveler");
        orig.setCp(527);
        em.persist(orig);

        clear();
        Pokemon actual = em.find(Pokemon.class, orig.getId());
        assertThat(actual, is(orig));
    }

    @Override
    public void testUpdateJPQL() {
        int updated = em.createQuery("UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.name = :name")
                .setParameter("newName", "Slowbro")
                .setParameter("newCp", 647)
                .setParameter("name", "Slowpoke")
                .executeUpdate();
        assertThat(updated, is(1));

        clear();
        Pokemon pokemon = result(em
                .createQuery("SELECT p FROM Pokemon p WHERE p.name=:name", Pokemon.class)
                .setParameter("name", "Slowbro"));
        assertThat(pokemon, is(not(nullValue())));
        assertThat(pokemon.getCp(), is(647));
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public void testUpdateCriteria() {
        CriteriaBuilder cb1 = em.getCriteriaBuilder();
        CriteriaUpdate<Pokemon> cu = cb1.createCriteriaUpdate(Pokemon.class);
        Root<Pokemon> r1 = cu.from(Pokemon.class);
        cu.where(cb1.equal(r1.get("name"), "Teddiursa"))
                .set("name", "Ursaring")
                .set("cp", 1568);
        int updated = em.createQuery(cu).executeUpdate();
        assertThat(updated, is(1));

        clear();
        CriteriaBuilder cb2 = em.getCriteriaBuilder();
        CriteriaQuery<Pokemon> cq = cb2.createQuery(Pokemon.class);
        Root<Pokemon> r2 = cq.from(Pokemon.class);
        cq.select(r2).where(cb2.equal(r2.get("name"), "Ursaring"));
        Pokemon pokemon = result(em.createQuery(cq));
        assertThat(pokemon, is(not(nullValue())));
        assertThat(pokemon.getCp(), is(1568));
    }

    @Override
    public void testUpdateCity() {
        List<String> origPokemonNames = new ArrayList<>();

        City origCity = result(em
                .createQuery("SELECT c FROM City c WHERE c.name = :name", City.class)
                .setParameter("name", "Saffron City"));
        assertThat(origCity, is(not(nullValue())));

        Stadium stadium = origCity.getStadium();
        Trainer trainer = stadium.getTrainer();
        List<Pokemon> pokemons = trainer.getPokemons();

        pokemons.stream()
                .map(Pokemon::getName)
                .forEach(origPokemonNames::add);

        Trainer newTrainer = new Trainer("Janine", 24);
        stadium.setTrainer(newTrainer);
        trainer.setPokemons(List.of());

        em.remove(trainer);
        em.persist(newTrainer);

        for (Pokemon pokemon : pokemons) {
            pokemon.setTrainer(newTrainer);
            em.persist(pokemon);
        }
        em.persist(stadium);

        Pokemon pokemon = pokemons.stream()
                .filter(p -> p.getName().equals("Alakazam"))
                .findFirst()
                .orElse(null);
        assertThat(pokemon, is(not(nullValue())));

        origPokemonNames.replaceAll(s -> s.equals("Alakazam") ? "Mega Alakazam" : s);

        int updated = em.createQuery("UPDATE Pokemon p SET p.name = :newName, p.cp = :newCp WHERE p.id = :id")
                .setParameter("newName", "Mega Alakazam")
                .setParameter("newCp", 4348)
                .setParameter("id", pokemon.getId())
                .executeUpdate();
        assertThat(updated, is(1));

        clear();
        City actualCity = em.find(City.class, origCity.getId());
        assertThat(actualCity, is(origCity));

        List<String> actualPokemonNames = actualCity.getStadium()
                .getTrainer()
                .getPokemons()
                .stream()
                .map(Pokemon::getName)
                .toList();

        assertThat(actualPokemonNames, containsInAnyOrder(origPokemonNames.toArray()));
    }
}
