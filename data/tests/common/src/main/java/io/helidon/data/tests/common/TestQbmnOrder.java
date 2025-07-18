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

import io.helidon.data.Order;
import io.helidon.data.OrderDirection;
import io.helidon.data.Sort;
import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.common.TestUtils.checkPokemonsSortedList;
import static io.helidon.data.tests.common.TestUtils.pokemonsList;
import static io.helidon.data.tests.common.TestUtils.sortedPokemonsListByHpAndName;

public class TestQbmnOrder {

    private static final System.Logger LOGGER = System.getLogger(TestQbmnOrder.class.getName());

    private static PokemonRepository pokemonRepository;

    @BeforeAll
    public static void before() {
        pokemonRepository = Services.get(PokemonRepository.class);
    }

    @AfterAll
    public static void after() {
        pokemonRepository = null;
    }

    // Static ordering (JPQL)
    @Test
    public void testFindAllOrderByHpAscName() {
        List<Pokemon> pokemons = pokemonRepository.findAllOrderByHpAscName();
        List<Pokemon> checkPokemons = sortedPokemonsListByHpAndName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic ordering (criteria API)
    @Test
    public void testFindAllOrderByHpAscDynamicName() {
        List<Pokemon> pokemons = pokemonRepository.findAllOrderByHp(Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByHpAndName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic ordering (criteria API) with case sensitivity turned off
    @Test
    public void testFindAllOrderByHpAscDynamicInsensitiveName() {
        List<Pokemon> pokemons = pokemonRepository.findAllOrderByHp(Sort.create(
                Order.create("name",
                             OrderDirection.ASC,
                             false)));
        List<Pokemon> checkPokemons = sortedPokemonsListByHpAndName(pokemonsList());
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

}
