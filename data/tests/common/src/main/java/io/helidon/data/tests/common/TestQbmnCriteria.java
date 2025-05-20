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

import io.helidon.data.DataRegistry;
import io.helidon.data.Order;
import io.helidon.data.Sort;
import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.repository.PokemonRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.common.InitialData.POKEMONS;
import static io.helidon.data.tests.common.TestUtils.checkPokemonsList;
import static io.helidon.data.tests.common.TestUtils.checkPokemonsSortedList;
import static io.helidon.data.tests.common.TestUtils.pokemonsById;
import static io.helidon.data.tests.common.TestUtils.sortedPokemonsListByName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestQbmnCriteria {

    private static PokemonRepository pokemonRepository;

    // Simple (JPQL) criteria After

    @BeforeAll
    public static void before(DataRegistry data) {
        pokemonRepository = data.repository(PokemonRepository.class);
    }

    @AfterAll
    public static void after() {
        pokemonRepository = null;
    }

    @Test
    public void testFindByNameAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByNameAfter("Lugia");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 6, 7, 8, 12, 13, 15, 18, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByIdAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByIdAfter(10);
        List<Pokemon> checkPokemons = pokemonsById(11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (JPQL) criteria After

    @Test
    public void testFindByNameNotAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotAfter("Lugia");
        List<Pokemon> checkPokemons = pokemonsById(5, 9, 10, 11, 14, 16, 17, 19);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByIdNotAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByIdNotAfter(10);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByNameAfter("Lugia",
                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 6, 7, 8, 12, 13, 15, 18, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByIdAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByIdAfter(10,
                                                                 Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria Before

    @Test
    public void testDynamicFindByNameNotAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotAfter("Lugia",
                                                                      Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(5, 9, 10, 11, 14, 16, 17, 19));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByIdNotAfter() {
        List<Pokemon> pokemons = pokemonRepository.findByIdNotAfter(10,
                                                                    Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByNameBefore("Lugia");
        List<Pokemon> checkPokemons = pokemonsById(5, 9, 10, 11, 14, 17, 19);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByIdBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByIdBefore(10);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (JPQL) criteria Before

    @Test
    public void testFindByNameNotBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotBefore("Lugia");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 6, 7, 8, 12, 13, 15, 16, 18, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByIdNotBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByIdNotBefore(10);
        List<Pokemon> checkPokemons = pokemonsById(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByNameBefore("Lugia",
                                                                    Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(5, 9, 10, 11, 14, 17, 19));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByIdBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByIdBefore(10,
                                                                  Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria Contains
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testDynamicFindByNameNotBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotBefore("Lugia",
                                                                       Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 6, 7, 8, 12, 13, 15, 16, 18, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByIdNotBefore() {
        List<Pokemon> pokemons = pokemonRepository.findByIdNotBefore(10,
                                                                     Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameContains() {
        List<Pokemon> pokemons = pokemonRepository.findByNameContains("ear");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameNotContains() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotContains("ear");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseContainsLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseContains("ear");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotContainsLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotContains("ear");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria Contains
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testFindByNameIgnoreCaseContainsUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseContains("EAR");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotContainsUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotContains("EAR");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameContains() {
        List<Pokemon> pokemons = pokemonRepository.findByNameContains("ear",
                                                                      Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameNotContains() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotContains("ear",
                                                                         Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseContainsLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseContains("ear",
                                                                                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotContainsLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotContains("ear",
                                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria EndsWith
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testDynamicFindByNameIgnoreCaseContainsUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseContains("EAR",
                                                                                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotContainsUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotContains("EAR",
                                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameEndsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameEndsWith("earow");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameNotEndsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotEndsWith("earow");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseEndsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseEndsWith("earow");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotEndsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotEndsWith("earow");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    //------------------------------------

    // Dynamic (criteria API) criteria EndsWith
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testFindByNameIgnoreCaseEndsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseEndsWith("EAROW");
        List<Pokemon> checkPokemons = pokemonsById(8, 9);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotEndsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotEndsWith("EAROW");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameEndsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameEndsWith("earow",
                                                                      Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameNotEndsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotEndsWith("earow",
                                                                         Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseEndsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseEndsWith("earow",
                                                                                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotEndsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotEndsWith("earow",
                                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria StartsWith
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testDynamicFindByNameIgnoreCaseEndsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseEndsWith("EAROW",
                                                                                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(8, 9));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotEndsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotEndsWith("EAROW",
                                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameStartsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameStartsWith("Sand");
        List<Pokemon> checkPokemons = pokemonsById(12, 13);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameNotStartsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotStartsWith("Sand");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseStartsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseStartsWith("Sand");
        List<Pokemon> checkPokemons = pokemonsById(12, 13);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotStartsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotStartsWith("Sand");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria StartsWith
    // Case sensitivity depends on database setup and tests may not always work as expected,
    // so upper case pattern for case-sensitive test is not present.

    @Test
    public void testFindByNameIgnoreCaseStartsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseStartsWith("SAND");
        List<Pokemon> checkPokemons = pokemonsById(12, 13);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotStartsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotStartsWith("SAND");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameStartsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameStartsWith("Sand",
                                                                        Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(12, 13));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameNotStartsWith() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotStartsWith("Sand",
                                                                           Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseStartsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseStartsWith("Sand",
                                                                                  Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(12, 13));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotStartsWithLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotStartsWith("Sand",
                                                                                     Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria LessThan, numbers

    @Test
    public void testDynamicFindByNameIgnoreCaseStartsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseStartsWith("SAND",
                                                                                  Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(12, 13));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotStartsWithUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotStartsWith("SAND",
                                                                                     Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria LessThan, numbers

    @Test
    public void testFindByHpLessThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpLessThan(140);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 14);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotLessThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotLessThan(140);
        List<Pokemon> checkPokemons = pokemonsById(4, 5, 13, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria LessThan, numbers

    @Test
    public void testDynamicFindByHpLessThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpLessThan(140,
                                                                    Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 14));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotLessThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotLessThan(140,
                                                                       Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(4, 5, 13, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria LessThan, numbers

    @Test
    public void testFindByHpLessThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpLessThanEqual(140);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotLessThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotLessThanEqual(140);
        List<Pokemon> checkPokemons = pokemonsById(4, 5, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria GreaterThan, numbers

    @Test
    public void testDynamicFindByHpLessThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpLessThanEqual(140,
                                                                         Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotLessThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotLessThanEqual(140,
                                                                            Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(4, 5, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria GreaterThan, numbers

    @Test
    public void testFindByHpGreaterThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpGreaterThan(140);
        List<Pokemon> checkPokemons = pokemonsById(4, 5, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotGreaterThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotGreaterThan(140);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria GreaterThanEqual, numbers

    @Test
    public void testDynamicFindByHpGreaterThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpGreaterThan(140,
                                                                       Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(4, 5, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotGreaterThan() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotGreaterThan(140,
                                                                          Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria GreaterThanEqual, numbers

    @Test
    public void testFindByHpGreaterThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpGreaterThanEqual(140);
        List<Pokemon> checkPokemons = pokemonsById(4, 5, 13, 15, 16, 17, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotGreaterThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotGreaterThanEqual(140);
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 14);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria Between, numbers

    @Test
    public void testDynamicFindByHpGreaterThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpGreaterThanEqual(140,
                                                                            Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(4, 5, 13, 15, 16, 17, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotGreaterThanEqual() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotGreaterThanEqual(140,
                                                                               Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 14));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria Between, numbers

    @Test
    public void testFindByHpBetween() {
        List<Pokemon> pokemons = pokemonRepository.findByHpBetween(115, 166);
        List<Pokemon> checkPokemons = pokemonsById(2, 3, 5, 9, 11, 13, 18, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotBetween() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotBetween(115, 166);
        List<Pokemon> checkPokemons = pokemonsById(1, 4, 6, 7, 8, 10, 12, 14, 15, 16, 17, 19);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria Like, String only

    @Test
    public void testDynamicFindByHpBetween() {
        List<Pokemon> pokemons = pokemonRepository.findByHpBetween(115, 166,
                                                                   Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(2, 3, 5, 9, 11, 13, 18, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotBetween() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotBetween(115, 166,
                                                                      Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 4, 6, 7, 8, 10, 12, 14, 15, 16, 17, 19));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameLike() {
        List<Pokemon> pokemons = pokemonRepository.findByNameLike("%gir%");
        List<Pokemon> checkPokemons = pokemonsById(19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameNotLike() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotLike("%gir%");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseLikeLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseLike("%gir%");
        List<Pokemon> checkPokemons = pokemonsById(19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotLikeLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotLike("%gir%");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria Like, String only

    @Test
    public void testFindByNameIgnoreCaseLikeUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseLike("%GIR%");
        List<Pokemon> checkPokemons = pokemonsById(19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameIgnoreCaseNotLikeUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotLike("%GIR%");
        List<Pokemon> checkPokemons = pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameLike() {
        List<Pokemon> pokemons = pokemonRepository.findByNameLike("%gir%",
                                                                  Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameNotLike() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotLike("%gir%",
                                                                     Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseLikeLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseLike("%gir%",
                                                                            Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotLikeLower() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotLike("%gir%",
                                                                               Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria In, String

    @Test
    public void testDynamicFindByNameIgnoreCaseLikeUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseLike("%GIR%",
                                                                            Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameIgnoreCaseNotLikeUpper() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIgnoreCaseNotLike("%GIR%",
                                                                               Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria In, Integer

    @Test
    public void testFindByNameIn() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIn(
                List.of("Pikachu", "Machop", "Charizard", "Magikarp", "Fearow",
                        "Arbok", "Sandslash", "Rayquaza", "Ho-Oh", "Giratina"));
        List<Pokemon> checkPokemons = pokemonsById(1, 3, 5, 7, 9, 11, 13, 15, 17, 19);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameNotIn() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotIn(
                List.of("Pikachu", "Machop", "Charizard", "Magikarp", "Fearow",
                        "Arbok", "Sandslash", "Rayquaza", "Ho-Oh", "Giratina"));
        List<Pokemon> checkPokemons = pokemonsById(2, 4, 6, 8, 10, 12, 14, 16, 18, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria In, String

    @Test
    public void testFindByHpIn() {
        List<Pokemon> pokemons = pokemonRepository.findByHpIn(List.of(132, 145, 47, 72, 193, 139));
        List<Pokemon> checkPokemons = pokemonsById(1, 3, 5, 7, 10, 16, 17);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByHpNotIn() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotIn(List.of(132, 145, 47, 72, 193, 139));
        List<Pokemon> checkPokemons = pokemonsById(2, 4, 6, 8, 9, 11, 12, 13, 14, 15, 18, 19, 20);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria In, Integer

    @Test
    public void testDynamicFindByNameIn() {
        List<Pokemon> pokemons = pokemonRepository.findByNameIn(
                List.of("Pikachu", "Machop", "Charizard", "Magikarp", "Fearow",
                        "Arbok", "Sandslash", "Rayquaza", "Ho-Oh", "Giratina"),
                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 3, 5, 7, 9, 11, 13, 15, 17, 19));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameNotIn() {
        List<Pokemon> pokemons = pokemonRepository.findByNameNotIn(
                List.of("Pikachu", "Machop", "Charizard", "Magikarp", "Fearow",
                        "Arbok", "Sandslash", "Rayquaza", "Ho-Oh", "Giratina"),
                Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(2, 4, 6, 8, 10, 12, 14, 16, 18, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    // Simple (JPQL) criteria AND/OR

    @Test
    public void testDynamicFindByHpIn() {
        List<Pokemon> pokemons = pokemonRepository.findByHpIn(List.of(132, 145, 47, 72, 193, 139),
                                                              Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(1, 3, 5, 7, 10, 16, 17));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByHpNotIn() {
        List<Pokemon> pokemons = pokemonRepository.findByHpNotIn(List.of(132, 145, 47, 72, 193, 139),
                                                                 Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(
                pokemonsById(2, 4, 6, 8, 9, 11, 12, 13, 14, 15, 18, 19, 20));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameAndHp() {
        Pokemon pokemon = POKEMONS[1];
        Optional<Pokemon> maybeFromDb = pokemonRepository.findByNameAndHp(pokemon.getName(), pokemon.getHp());
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    @Test
    public void testFindByNameOrHp() {
        List<Pokemon> pokemons = pokemonRepository.findByNameOrHp("Raichu", 193);
        List<Pokemon> checkPokemons = pokemonsById(2, 16, 17);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByNameOrHpOrId() {
        List<Pokemon> pokemons = pokemonRepository.findByNameOrHpOrId("Raichu", 193, 19);
        List<Pokemon> checkPokemons = pokemonsById(2, 16, 17, 19);
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Dynamic (criteria API) criteria AND/OR

    @Test
    public void testFindByNameAndHpOrId() {
        List<Pokemon> pokemons = pokemonRepository.findByNameAndHpOrId("Machop", 132, 17);
        List<Pokemon> checkPokemons = pokemonsById(3, 17);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testFindByIdOrHpAndName() {
        List<Pokemon> pokemons = pokemonRepository.findByIdOrHpAndName(18, 285, "Snorlax");
        List<Pokemon> checkPokemons = pokemonsById(4, 18);
        checkPokemonsList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameAndHp() {
        Pokemon pokemon = POKEMONS[1];
        Optional<Pokemon> maybeFromDb = pokemonRepository.findByNameAndHp(pokemon.getName(), pokemon.getHp(),
                                                                          Sort.create(Order.create("name")));
        assertThat(maybeFromDb.isPresent(), is(true));
        Pokemon fromDb = maybeFromDb.get();
        assertThat(fromDb, is(pokemon));
    }

    @Test
    public void testDynamicFindByNameOrHp() {
        List<Pokemon> pokemons = pokemonRepository.findByNameOrHp("Raichu", 193,
                                                                  Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsById(2, 16, 17));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameOrHpOrId() {
        List<Pokemon> pokemons = pokemonRepository.findByNameOrHpOrId("Raichu", 193, 19,
                                                                      Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsById(2, 16, 17, 19));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByNameAndHpOrId() {
        List<Pokemon> pokemons = pokemonRepository.findByNameAndHpOrId("Machop", 132, 17,
                                                                       Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsById(3, 17));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

    @Test
    public void testDynamicFindByIdOrHpAndName() {
        List<Pokemon> pokemons = pokemonRepository.findByIdOrHpAndName(18, 285, "Snorlax",
                                                                       Sort.create(Order.create("name")));
        List<Pokemon> checkPokemons = sortedPokemonsListByName(pokemonsById(4, 18));
        checkPokemonsSortedList(pokemons, checkPokemons);
    }

}
