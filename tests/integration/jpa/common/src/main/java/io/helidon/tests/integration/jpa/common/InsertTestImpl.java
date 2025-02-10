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
import io.helidon.tests.integration.jpa.common.model.Type;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actual implementation of {@link InsertTest}.
 */
@Transactional
@ApplicationScoped
@SuppressWarnings({"SpellCheckingInspection", "ResultOfMethodCallIgnored"})
public class InsertTestImpl extends AbstractTestImpl implements InsertTest {

    @Override
    public void testInsertType() {
        Type type = new Type(20, "TestType");
        em.persist(type);
        em.flush();
        Type dbType = em.find(Type.class, 20);
        assertThat(dbType, is(type));
    }

    @Override
    public void testInsertTrainerWithPokemons() {
        Trainer origTrainer = new Trainer("Gary Oak", 10);
        List<Pokemon> origPokemons = List.of(
                new Pokemon(origTrainer, "Krabby", 236, List.of(dataSet.water())),
                new Pokemon(origTrainer, "Nidoran", 251, List.of(dataSet.poison())),
                new Pokemon(origTrainer, "Eevee", 115, List.of(dataSet.normal())),
                new Pokemon(origTrainer, "Electivire", 648, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Dodrio", 346, List.of(dataSet.normal(), dataSet.flying())),
                new Pokemon(origTrainer, "Magmar", 648, List.of(dataSet.fire())));

        em.persist(origTrainer);
        origPokemons.forEach(em::persist);
        em.flush();

        clear();
        Trainer actualTrainer = em.find(Trainer.class, origTrainer.getId());
        List<Pokemon> actualPokemons = List.copyOf(actualTrainer.getPokemons());
        actualPokemons.forEach(Pokemon::getTypes); // lazy fetch
        assertThat(actualTrainer, is(origTrainer));
        assertThat(actualPokemons, is(origPokemons));
    }

    @Override
    public void testTownWithStadium() {
        Trainer origTrainer = new Trainer("Lt. Surge", 28);
        List<Pokemon> origPokemons = List.of(
                new Pokemon(origTrainer, "Raichu", 1521, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Manectric", 1589, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Magnezone", 1853, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Electrode", 1237, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Pachirisu", 942, List.of(dataSet.electric())),
                new Pokemon(origTrainer, "Electivire", 1931, List.of(dataSet.electric())));
        Stadium origStadium = new Stadium("Vermilion Gym", origTrainer);
        City origCity = new City("Vermilion City", "Mina", origStadium);

        em.persist(origTrainer);
        origPokemons.forEach(em::persist);
        em.persist(origStadium);
        em.persist(origCity);

        clear();
        City actualCity = em.find(City.class, origCity.getId());
        assertThat(actualCity, is(not(nullValue())));
        List<Pokemon> actualPokemons = List.copyOf(actualCity.getStadium().getTrainer().getPokemons());
        actualPokemons.forEach(Pokemon::getTypes); // lazy fetch
        assertThat(actualCity, is(origCity));
        assertThat(actualPokemons, is(origPokemons));
    }
}
