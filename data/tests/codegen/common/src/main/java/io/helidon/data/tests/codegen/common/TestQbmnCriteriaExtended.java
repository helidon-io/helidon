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
package io.helidon.data.tests.codegen.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.helidon.data.api.DataRegistry;
import io.helidon.data.api.Order;
import io.helidon.data.api.Sort;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.TestUtils.checkPokemonsList;
import static io.helidon.data.tests.codegen.common.TestUtils.checkPokemonsSortedList;
import static io.helidon.data.tests.codegen.common.TestUtils.pokemonsList;
import static io.helidon.data.tests.codegen.common.TestUtils.sortedPokemonsListByName;

public class TestQbmnCriteriaExtended {

    private static final Map<Integer, Pokemon> EMPTY_POKEMONS = Map.of(
            200, new Pokemon(200, null, "Pansear", 54, false, Collections.emptyList()),
            201, new Pokemon(201, null, "Simisear", 102, false, Collections.emptyList()),
            202, new Pokemon(202, null, "", 102, false, Collections.emptyList())
    );

    private static PokemonRepository pokemonRepository;

    // Simple (JPQL) criteria Empty

    @Test
    public void testFindByTypeEmpty() {
        List<Pokemon> pokemons = pokemonRepository.findByTypesEmpty();
        List<Pokemon> checkPokemons = List.copyOf(EMPTY_POKEMONS.values());
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByTypeNotEmpty() {
        List<Pokemon> pokemons = pokemonRepository.findByTypesNotEmpty();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria Empty

    @Test
    public void testDynamicFindByTypeEmpty() {
        List<Pokemon> pokemons = pokemonRepository.findByTypesEmpty(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(new ArrayList<>(EMPTY_POKEMONS.values()));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByTypeNotEmpty() {
        List<Pokemon> pokemons = pokemonRepository.findByTypesNotEmpty(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria Null

    @Test
    public void testFindByTrainerNull() {
        List<Pokemon> pokemons = pokemonRepository.findByTrainerNull();
        List<Pokemon> checkPokemons = List.copyOf(EMPTY_POKEMONS.values());
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByTrainerNotNull() {
        List<Pokemon> pokemons = pokemonRepository.findByTrainerNotNull();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria Null

    @Test
    public void testDynamicFindByTrainerNull() {
        List<Pokemon> pokemons = pokemonRepository.findByTrainerNull(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(new ArrayList<>(EMPTY_POKEMONS.values()));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByTrainerNotNull() {
        List<Pokemon> pokemons = pokemonRepository.findByTrainerNotNull(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria True

    @Test
    public void testFindByTrainerTrue() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveTrue();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByTrainerNotTrue() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveNotTrue();
        List<Pokemon> checkPokemons = List.copyOf(EMPTY_POKEMONS.values());
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria True

    @Test
    public void testDynamicFindByTrainerTrue() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveTrue(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByTrainerNotTrue() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveNotTrue(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(new ArrayList<>(EMPTY_POKEMONS.values()));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria False

    @Test
    public void testFindByTrainerFalse() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveFalse();
        List<Pokemon> checkPokemons = List.copyOf(EMPTY_POKEMONS.values());
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByTrainerNotFalse() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveNotFalse();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria False

    @Test
    public void testDynamicFindByTrainerFalse() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveFalse(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(new ArrayList<>(EMPTY_POKEMONS.values()));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByTrainerNotFalse() {
        List<Pokemon> pokemons = pokemonRepository.findByAliveNotFalse(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @BeforeAll
    public static void before(DataRegistry data) {
        pokemonRepository = data.repository(PokemonRepository.class);
        EMPTY_POKEMONS.keySet().forEach(key -> pokemonRepository.insert(EMPTY_POKEMONS.get(key)));
    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        pokemonRepository = null;
    }

}
