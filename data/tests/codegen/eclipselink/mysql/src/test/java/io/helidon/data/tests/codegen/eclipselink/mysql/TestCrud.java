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
package io.helidon.data.tests.codegen.eclipselink.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.data.DataRegistry;
import io.helidon.data.tests.codegen.common.InitialData;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.model.Trainer;
import io.helidon.data.tests.codegen.model.Type;
import io.helidon.data.tests.codegen.repository.PokemonRepository;
import io.helidon.data.tests.codegen.repository.TrainerRepository;
import io.helidon.data.tests.codegen.repository.TypeRepository;
import io.helidon.testing.integration.junit5.suite.Suite;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

@Suite(MySqlSuite.class)
public class TestCrud {

    private final DataRegistry data;

    private final PokemonRepository pokemonRepository;
    private final TrainerRepository trainerRepository;
    private final TypeRepository typeRepository;

    TestCrud(DataRegistry data) {
        this.data = data;
        this.pokemonRepository = data.repository(PokemonRepository.class);
        this.trainerRepository = data.repository(TrainerRepository.class);
        this.typeRepository = data.repository(TypeRepository.class);
    }

    @Test
    void testFindCritterById() {
        Optional<Pokemon> critterFromDb = pokemonRepository.findById(InitialData.POKEMONS[1].getId());
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(InitialData.POKEMONS[1]));
        });
    }

    @Test
    void testExistsCritterById() {
        boolean resultExisting = pokemonRepository.existsById(InitialData.POKEMONS[2].getId());
        assertThat(resultExisting, is(true));
        boolean resultNonExisting = pokemonRepository.existsById(200);
        assertThat(resultNonExisting, is(false));
    }

    @Test
    void testFindAllKinds() {
        List<Type> kinds = new ArrayList<>();
        typeRepository.findAll().forEach(kinds::add);
        assertThat(kinds, hasSize(equalTo(InitialData.TYPES.length - 1)));
        kinds.forEach(kind -> {
            assertThat(kind.getId(), is(lessThan(InitialData.TYPES.length)));
            assertThat(kind, is(InitialData.TYPES[kind.getId()]));
        });
    }

    @Test
    void testCountAllKinds() {
        long count = typeRepository.count();
        assertThat(count, is((long) (InitialData.TYPES.length - 1)));
    }

    // Uses Critter ID 100
    @Test
    void testSaveCritter() {
        Pokemon critter = buildCritter(InitialData.NEW_POKEMONS.get(100));
        pokemonRepository.save(critter);
        Optional<Pokemon> critterFromDb = pokemonRepository.findById(100);
        assertThat(critterFromDb.isPresent(), is(true));
        assertThat(critterFromDb.get(), is(critter));
    }

    // Uses Critter ID 101 and 102
    @Test
    void testSaveAllCritters() {
        List<Pokemon> critters = List.of(buildCritter(InitialData.NEW_POKEMONS.get(101)),
                                         buildCritter(InitialData.NEW_POKEMONS.get(102)));
        pokemonRepository.saveAll(critters);
        Optional<Pokemon> critterFromDb101 = pokemonRepository.findById(101);
        assertThat(critterFromDb101.isPresent(), is(true));
        assertThat(critterFromDb101.get(), is(critters.get(0)));
        Optional<Pokemon> critterFromDb102 = pokemonRepository.findById(102);
        assertThat(critterFromDb102.isPresent(), is(true));
        assertThat(critterFromDb102.get(), is(critters.get(1)));
    }

    // Uses Critter ID 3: evolve Machop to Machoke
    @Test
    void testUpdateCritter() {
        Pokemon critter = pokemonRepository.findById(InitialData.POKEMONS[3].getId()).orElseThrow();
        critter.setName("Machoke");
        pokemonRepository.update(critter);
        Optional<Pokemon> critterFromDb = pokemonRepository.findById(InitialData.POKEMONS[3].getId());
        assertThat(critterFromDb.isPresent(), is(true));
        assertThat(critterFromDb.get(), is(critter));
    }

    // Uses Critter ID 6: evolve Meowth to Persian
    // Uses Critter ID 7: evolve Magikarp to Gyarados
    @Test
    void testUpdateAllCritters() {
        Pokemon meowth = pokemonRepository.findById(InitialData.POKEMONS[6].getId()).orElseThrow();
        meowth.setName("Persian");
        Pokemon magikarp = pokemonRepository.findById(InitialData.POKEMONS[7].getId()).orElseThrow();
        magikarp.setName("Gyarados");
        List<Type> gyaradosKinds = new ArrayList<>(magikarp.getTypes());
        gyaradosKinds.add(typeRepository.findById(InitialData.TYPES[3].getId()).orElseThrow());
        magikarp.setTypes(gyaradosKinds);
        pokemonRepository.updateAll(List.of(meowth, magikarp));
        Optional<Pokemon> meowthFromDb = pokemonRepository.findById(InitialData.POKEMONS[6].getId());
        assertThat(meowthFromDb.isPresent(), is(true));
        assertThat(meowthFromDb.get(), is(meowth));
        Optional<Pokemon> magikarpFromDb = pokemonRepository.findById(InitialData.POKEMONS[7].getId());
        assertThat(magikarpFromDb.isPresent(), is(true));
        assertThat(magikarpFromDb.get(), is(magikarp));
    }

    // Deletes Critter ID 17
    @Test
    void testDeleteCritterById() {
        pokemonRepository.deleteById(InitialData.POKEMONS[17].getId());
        boolean result = pokemonRepository.existsById(InitialData.POKEMONS[17].getId());
        assertThat(result, is(false));
    }

    // Deletes Critter ID 18
    @Test
    void testDeleteCritter() {
        Pokemon critter = pokemonRepository.findById(InitialData.POKEMONS[18].getId()).orElseThrow();
        pokemonRepository.delete(critter);
        boolean result = pokemonRepository.existsById(InitialData.POKEMONS[18].getId());
        assertThat(result, is(false));
    }

    // Deletes Critter ID 19 and 20
    @Test
    void testDeleteAllCritters() {
        List<Pokemon> critters = List.of(pokemonRepository.findById(InitialData.POKEMONS[19].getId()).orElseThrow(),
                                         pokemonRepository.findById(InitialData.POKEMONS[20].getId()).orElseThrow());
        pokemonRepository.deleteAll(critters);
        boolean result19 = pokemonRepository.existsById(InitialData.POKEMONS[19].getId());
        assertThat(result19, is(false));
        boolean result20 = pokemonRepository.existsById(InitialData.POKEMONS[20].getId());
        assertThat(result20, is(false));
    }

    // Rebuild Critter using managed instances.
    private Pokemon buildCritter(Pokemon source) {
        List<Type> kinds = new ArrayList<>(source.getTypes().size());
        source.getTypes().forEach(kind -> typeRepository.findById(kind.getId())
                .ifPresent(kinds::add));
        Trainer keeper = trainerRepository.findById(source.getTrainer().getId()).orElseThrow();
        return new Pokemon(source.getId(), keeper, source.getName(), kinds);
    }

}
