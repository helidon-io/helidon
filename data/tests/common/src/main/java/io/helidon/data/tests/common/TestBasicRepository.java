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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.model.Region;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.data.tests.repository.RegionRepository;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.common.InitialData.NEW_POKEMONS;
import static io.helidon.data.tests.common.InitialData.REGIONS;
import static io.helidon.data.tests.common.InitialData.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TestBasicRepository {

    private static RegionRepository regionRepository;
    private static PokemonRepository pokemonRepository;

    @BeforeAll
    public static void before() {
        regionRepository = Services.get(RegionRepository.class);
        pokemonRepository = Services.get(PokemonRepository.class);
        // Used in testSaveExisting()
        pokemonRepository.insert(NEW_POKEMONS.get(106));
        // Used in testSaveAllExisting()
        pokemonRepository.insert(NEW_POKEMONS.get(107));
        pokemonRepository.insert(NEW_POKEMONS.get(108));
    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        regionRepository = null;
        pokemonRepository = null;
    }

    // Add new pokemon and verify it in the database
    @Test
    public void testSave() {
        Pokemon pokemon = NEW_POKEMONS.get(100);
        pokemonRepository.save(pokemon);
        Optional<Pokemon> maybeFromDb = pokemonRepository.findById(100);
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    // Add new pokemons List and verify it in the database
    @Test
    public void testSaveAll() {
        List<Pokemon> pokemons = List.of(NEW_POKEMONS.get(101),
                                         NEW_POKEMONS.get(102),
                                         NEW_POKEMONS.get(103));
        pokemonRepository.saveAll(pokemons);
        for (Pokemon pokemon : pokemons) {
            Optional<Pokemon> maybeFromDb = pokemonRepository.findById(pokemon.getId());
            assertThat(maybeFromDb.isPresent(), is(true));
            Pokemon fromDb = maybeFromDb.get();
            assertThat(fromDb, is(pokemon));
        }
    }

    // Save existing pokemon and verify it in the database
    @Test
    public void testSaveExisting() {
        Pokemon pokemon = NEW_POKEMONS.get(106);
        // Verify that pokemon is present in the database
        boolean exists = pokemonRepository.existsById(pokemon.getId());
        assertThat(exists, is(true));
        // Modify Charizard to Charmeleon
        pokemon.setName("Charmeleon");
        pokemon.setTypes(List.of(TYPES[10]));
        pokemon.setHp(75);
        // Save and verify
        pokemonRepository.save(pokemon);
        Optional<Pokemon> maybeFromDb = pokemonRepository.findById(106);
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    // Save existing pokemons List and verify it in the database
    @Test
    public void testSaveAllExisting() {
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
        pokemonRepository.saveAll(pokemons);
        for (Pokemon pokemon : pokemons) {
            Optional<Pokemon> maybeFromDb = pokemonRepository.findById(pokemon.getId());
            assertThat(maybeFromDb.isPresent(), is(true));
            Pokemon fromDb = maybeFromDb.get();
            assertThat(fromDb, is(pokemon));
        }
    }

    // Find specific region by ID
    @Test
    public void testFindById() {
        Region region = REGIONS[1];
        Optional<Region> maybeFromDb = regionRepository.findById(region.getId());
        assertThat(maybeFromDb.isPresent(), is(true));
        Region fromDb = maybeFromDb.get();
        assertThat(fromDb, is(region));
    }

    // Check existence of specific region by ID
    @Test
    public void testExistsById() {
        // Existing ID
        Region region = REGIONS[2];
        boolean exists = regionRepository.existsById(region.getId());
        assertThat(exists, is(true));
        // Non existing ID
        boolean notExists = regionRepository.existsById(100);
        assertThat(notExists, is(false));
    }

    // Find all regions and verify them against source data array
    @Test
    public void testFindAll() {
        // Regions from the database using findAll call
        List<Region> regions = regionRepository.findAll().toList();
        assertThat(regions, hasSize(REGIONS.length - 1));
        // Set of all regions in source data array
        Set<Region> checkRegions = new HashSet<>(REGIONS.length);
        for (int i = 1; i < REGIONS.length; i++) {
            checkRegions.add(REGIONS[i]);
        }
        // Verify that exactly regions from source data array were returned
        for (Region region : regions) {
            assertThat(checkRegions.contains(region), is(true));
            checkRegions.remove(region);
        }
        assertThat(checkRegions, empty());
    }

    // Get count of all regions and verify them against source data array
    @Test
    public void testCount() {
        long count = regionRepository.count();
        assertThat(count, is((long) (REGIONS.length - 1)));
    }

}
