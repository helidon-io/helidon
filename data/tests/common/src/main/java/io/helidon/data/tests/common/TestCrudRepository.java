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
package io.helidon.data.tests.common;

import java.util.List;
import java.util.Optional;

import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.common.InitialData.NEW_POKEMONS;
import static io.helidon.data.tests.common.InitialData.POKEMONS;
import static io.helidon.data.tests.common.InitialData.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class TestCrudRepository {

    private static final System.Logger LOGGER = System.getLogger(TestCrudRepository.class.getName());

    private static PokemonRepository pokemonRepository;

    @BeforeAll
    public static void before() {
        pokemonRepository = Services.get(PokemonRepository.class);
        // Used in testUpdate()
        pokemonRepository.insert(NEW_POKEMONS.get(106));
        // Used in testUpdateAll()
        pokemonRepository.insert(NEW_POKEMONS.get(107));
        pokemonRepository.insert(NEW_POKEMONS.get(108));

    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        pokemonRepository = null;
    }

    // Add new pokemon and verify it in the database
    @Test
    public void testInsert() {
        Pokemon pokemon = NEW_POKEMONS.get(100);
        pokemonRepository.insert(pokemon);
        Optional<Pokemon> maybeFromDb = pokemonRepository.findById(100);
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    // Add new pokemons List and verify it in the database
    @Test
    public void testInsertAll() {
        List<Pokemon> pokemons = List.of(NEW_POKEMONS.get(101),
                                         NEW_POKEMONS.get(102),
                                         NEW_POKEMONS.get(103));
        pokemonRepository.insertAll(pokemons);
        for (Pokemon pokemon : pokemons) {
            Optional<Pokemon> maybeFromDb = pokemonRepository.findById(pokemon.getId());
            assertThat(maybeFromDb.isPresent(), is(true));
            Pokemon fromDb = maybeFromDb.get();
            assertThat(fromDb, is(pokemon));
        }
    }

    // Add already existing pokemon shall fail
    @Test
    public void testInsertExisting() {
        Pokemon pokemon = POKEMONS[1];
        assertThrows(RuntimeException.class, () -> pokemonRepository.insert(pokemon));
    }

    // Add pokemons List with already existing pokemon shall fail
    @Test
    public void testInsertAllExisting() {
        List<Pokemon> pokemons = List.of(NEW_POKEMONS.get(104),
                                         POKEMONS[1],
                                         NEW_POKEMONS.get(105));
        assertThrows(RuntimeException.class, () -> pokemonRepository.insertAll(pokemons));
    }

    // Update existing pokemon and verify it in the database
    @Test
    public void testUpdate() {
        Pokemon pokemon = NEW_POKEMONS.get(106);
        // Verify that pokemon is present in the database
        boolean exists = pokemonRepository.existsById(pokemon.getId());
        assertThat(exists, is(true));
        // Modify Charizard to Charmeleon
        pokemon.setName("Charmeleon");
        pokemon.setTypes(List.of(TYPES[10]));
        pokemon.setHp(75);
        // Save and verify
        pokemonRepository.update(pokemon);
        Optional<Pokemon> maybeFromDb = pokemonRepository.findById(106);
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    // Update existing pokemons List and verify it in the database
    @Test
    public void testUpdateAll() {
        List<Pokemon> pokemons = List.of(NEW_POKEMONS.get(106),
                                         NEW_POKEMONS.get(107));
        // Verify that pokemons are present in the database
        for (Pokemon pokemon : pokemons) {
            boolean exists = pokemonRepository.existsById(pokemon.getId());
            assertThat(exists, is(true));
        }
        // Modify Meowth to Alolan Persian
        pokemons.get(0).setName("Persian");
        pokemons.get(0).setTypes(List.of(TYPES[17]));
        pokemons.get(0).setHp(75);
        // Modify Magikarp to Alolan Persian
        pokemons.get(1).setName("Gyarados");
        pokemons.get(1).setTypes(List.of(TYPES[3], TYPES[11]));
        pokemons.get(1).setHp(150);
        // Save and verify
        pokemonRepository.updateAll(pokemons);
        for (Pokemon pokemon : pokemons) {
            Optional<Pokemon> maybeFromDb = pokemonRepository.findById(pokemon.getId());
            assertThat(maybeFromDb.isPresent(), is(true));
            Pokemon fromDb = maybeFromDb.get();
            assertThat(fromDb, is(pokemon));
        }
    }

    // Update not existing pokemon shall fail
    // TODO: Not implemented yet
    @Test
    public void testUpdateNotExisting() {
        LOGGER.log(System.Logger.Level.DEBUG, "Skipping testUpdateNotExisting: Not implemented yet");
    }

    // Update pokemons List with not existing pokemon shall fail
    // TODO: Not implemented yet
    @Test
    public void testUpdateAllNotExisting() {
        LOGGER.log(System.Logger.Level.DEBUG, "Skipping testUpdateAllNotExisting: Not implemented yet");
    }

}
