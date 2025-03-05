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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.data.api.DataRegistry;
import io.helidon.data.api.Order;
import io.helidon.data.api.Sort;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.InitialData.POKEMONS;
import static io.helidon.data.tests.codegen.common.TestUtils.POKEMONS_LIST;
import static io.helidon.data.tests.codegen.common.TestUtils.checkSortedPokemonsListByName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestQbmnProjection {


    private static PokemonRepository pokemonRepository;

    // Simple (JPQL) get projection

    @Test
    public void testGetByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[1];
        Pokemon result = pokemonRepository.getByName(pokemon.getName());
        assertThat(result, notNullValue());
        assertThat(result, is(pokemon));
    }

    @Test
    public void testGetMissingByName() {
        // Pokemon is not in the database, RuntimeException shall be thrown
        assertThrows(RuntimeException.class, () -> pokemonRepository.getByName("Beedrill"));
    }

    // Simple (JPQL) Optional<Entity> get projection

    @Test
    public void testOptionalGetByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[2];
        Optional<Pokemon> result = pokemonRepository.optionalGetByName(pokemon.getName());
        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(pokemon));
    }

    @Test
    public void testOptionalGetMissingByName() {
        // Pokemon is not in the database
        Optional<Pokemon> result =  pokemonRepository.optionalGetByName("Kakuna");
        assertThat(result.isPresent(), is(false));
    }

    // Dynamic (criteria API) get projection
    // Sort makes no sense in those kind of projections, but it shall work too because SQL allows it

    @Test
    public void testDynamicGetByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[1];
        Pokemon result = pokemonRepository.getByName(pokemon.getName(), Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(pokemon));
    }

    @Test
    public void testDynamicGetMissingByName() {
        // Pokemon is not in the database, RuntimeException shall be thrown
        assertThrows(RuntimeException.class, () -> pokemonRepository.getByName("Beedrill", Sort.create(Order.create("name"))));
    }

    // Dynamic (criteria API) Optional<Entity> get projection
    // Sort makes no sense in those kind of projections, but it shall work too because SQL allows it

    @Test
    public void testDynamicOptionalGetByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[2];
        Optional<Pokemon> result = pokemonRepository.optionalGetByName(pokemon.getName(), Sort.create(Order.create("name")));
        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(pokemon));
    }

    @Test
    public void testDynamicOptionalGetMissingByName() {
        // Pokemon is not in the database
        Optional<Pokemon> result =  pokemonRepository.optionalGetByName("Kakuna", Sort.create(Order.create("name")));
        assertThat(result.isPresent(), is(false));
    }

    // Simple (JPQL) List<Entity> find projection

    @Test
    public void testListFind() {
        List<Pokemon> pokemons = pokemonRepository.find();
        assertThat(pokemons, hasSize(POKEMONS_LIST.size()));
    }

    // Simple (JPQL) Stream<Entity> find projection

    @Test
    public void testStreamFind() {
        Stream<Pokemon> pokemons = pokemonRepository.streamFindAll();
        assertThat(pokemons.toList(), hasSize(POKEMONS_LIST.size()));
    }

    // Simple (JPQL) Collection<Entity> find projection

    @Test
    public void testCollectionFind() {
        Collection<Pokemon> pokemons = pokemonRepository.collectonFindAll();
        assertThat(pokemons, hasSize(POKEMONS_LIST.size()));
    }

    // Simple (JPQL) Stream<Entity> stream all projection

    @Test
    public void testStream() {
        Stream<Pokemon> pokemons = pokemonRepository.stream();
        assertThat(pokemons.toList(), hasSize(POKEMONS_LIST.size()));
    }

    @Test
    public void testStreamAll() {
        Stream<Pokemon> pokemons = pokemonRepository.streamAll();
        assertThat(pokemons.toList(), hasSize(POKEMONS_LIST.size()));
    }

    // Simple (JPQL) List<Entity> list all projection

    @Test
    public void testList() {
        List<Pokemon> pokemons = pokemonRepository.list();
        assertThat(pokemons, hasSize(POKEMONS_LIST.size()));
    }

    @Test
    public void testListAll() {
        List<Pokemon> pokemons = pokemonRepository.listAll();
        assertThat(pokemons, hasSize(POKEMONS_LIST.size()));
    }

    // Dynamic (criteria API) List<Entity> find projection

    @Test
    public void testDynamicListFind() {
        List<Pokemon> pokemons = pokemonRepository.find(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    // Dynamic (criteria API) Stream<Entity> find projection

    @Test
    public void testDynamicStreamFind() {
        Stream<Pokemon> pokemons = pokemonRepository.streamFindAll(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    // Dynamic (criteria API) Collection<Entity> find projection

    @Test
    public void testDynamicCollectionFind() {
        Collection<Pokemon> pokemons = pokemonRepository.collectonFindAll(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons.stream().toList());
    }

    // Dynamic (criteria API) Stream<Entity> stream all projection

    @Test
    public void testDynamicStream() {
        Stream<Pokemon> pokemons = pokemonRepository.stream(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    @Test
    public void testDynamicStreamAll() {
        Stream<Pokemon> pokemons = pokemonRepository.streamAll(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    // Dynamic (criteria API) List<Entity> list all projection

    @Test
    public void testDynamicList() {
        List<Pokemon> pokemons = pokemonRepository.list(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    @Test
    public void testDynamicListAll() {
        List<Pokemon> pokemons = pokemonRepository.listAll(Sort.create(Order.create("name")));
        checkSortedPokemonsListByName(pokemons);
    }

    // Simple (JPQL) exists projection

    @Test
    public void testExistsByName() {
        // Pokemon is in the database
        boolean result = pokemonRepository.existsByName("Snorlax");
        assertThat(result, is(true));
    }

    @Test
    public void testNotExistsByName() {
        // Pokemon is not in the database
        boolean result = pokemonRepository.existsByName("Articuno");
        assertThat(result, is(false));
    }

    @Test
    public void testBoxedExistsByName() {
        // Pokemon is in the database
        boolean result = pokemonRepository.boxedExistsByName("Meowth");
        assertThat(result, is(true));
    }

    @Test
    public void testBoxedNotExistsByName() {
        // Pokemon is not in the database
        boolean result = pokemonRepository.boxedExistsByName("Zapdos");
        assertThat(result, is(false));
    }

    // Dynamic (criteria API) exists projection

    @Test
    public void testDynamicExistsByName() {
        // Pokemon is in the database
        boolean result = pokemonRepository.existsByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(true));
    }

    @Test
    public void testDynamicNotExistsByName() {
        // Pokemon is not in the database
        boolean result = pokemonRepository.existsByName("Articuno", Sort.create(Order.create("name")));
        assertThat(result, is(false));
    }

    @Test
    public void testDynamicBoxedExistsByName() {
        // Pokemon is in the database
        boolean result = pokemonRepository.boxedExistsByName("Meowth", Sort.create(Order.create("name")));
        assertThat(result, is(true));
    }

    @Test
    public void testDynamicBoxedNotExistsByName() {
        // Pokemon is not in the database
        boolean result = pokemonRepository.boxedExistsByName("Zapdos", Sort.create(Order.create("name")));
        assertThat(result, is(false));
    }

    // Simple (JPQL) count projection

    @Test
    public void testCountByName() {
        // Pokemon is in the database
        Number result = pokemonRepository.countByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result.intValue(), is(1));
  }

    @Test
    public void testEmptyCountByName() {
        // Pokemon is not in the database
        Number result = pokemonRepository.countByName("Moltres");
        assertThat(result, notNullValue());
        assertThat(result.intValue(), is(0));
    }

    @Test
    public void testLongCountByName() {
        // Pokemon is in the database
        long result = pokemonRepository.longCountByName("Snorlax");
        assertThat(result, is(1L));
    }

    @Test
    public void testBoxedLongCountByName() {
        // Pokemon is in the database
        Long result = pokemonRepository.boxedLongCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is(1L));
    }

    @Test
    public void testIntCountByName() {
        // Pokemon is in the database
        int result = pokemonRepository.intCountByName("Snorlax");
        assertThat(result, is(1));
    }

    @Test
    public void testBoxedIntCountByName() {
        // Pokemon is in the database
        Integer result = pokemonRepository.boxedIntCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is(1));
    }

    @Test
    public void testShortCountByName() {
        // Pokemon is in the database
        short result = pokemonRepository.shortCountByName("Snorlax");
        assertThat(result, is((short) 1));
    }

    @Test
    public void testBoxedShortCountByName() {
        // Pokemon is in the database
        Short result = pokemonRepository.boxedShortCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is((short) 1));
    }

    @Test
    public void testByteCountByName() {
        // Pokemon is in the database
        byte result = pokemonRepository.byteCountByName("Snorlax");
        assertThat(result, is((byte) 1));
    }

    @Test
    public void testBoxedByteCountByName() {
        // Pokemon is in the database
        Byte result = pokemonRepository.boxedByteCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is((byte) 1));
    }

    @Test
    public void testFloatCountByName() {
        // Pokemon is in the database
        float result = pokemonRepository.floatCountByName("Snorlax");
        assertThat(result, is(1F));
    }

    @Test
    public void testBoxedFloatCountByName() {
        // Pokemon is in the database
        Float result = pokemonRepository.boxedFloatCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is(1F));
    }

    @Test
    public void testDoubleCountByName() {
        // Pokemon is in the database
        double result = pokemonRepository.doubleCountByName("Snorlax");
        assertThat(result, is(1D));
    }

    @Test
    public void testBoxedDoubleCountByName() {
        // Pokemon is in the database
        Double result = pokemonRepository.boxedDoubleCountByName("Snorlax");
        assertThat(result, notNullValue());
        assertThat(result, is(1D));
    }

    @Test
    public void testBigIntegerCountByName() {
        // Pokemon is in the database
        BigInteger result = pokemonRepository.bigIntegerCountByName("Snorlax");
        assertThat(result, is(BigInteger.valueOf(1L)));
    }

    @Test
    public void testBigDecimalCountByName() {
        // Pokemon is in the database
        BigDecimal result = pokemonRepository.bigDecimalCountByName("Snorlax");
        assertThat(result, is(BigDecimal.valueOf(1L)));
    }

    // Dynamic (criteria API) count projection

    @Test
    public void testDynamicCountByName() {
        // Pokemon is in the database
        Number result = pokemonRepository.countByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result.intValue(), is(1));
    }

    @Test
    public void testDynamicEmptyCountByName() {
        // Pokemon is not in the database
        Number result = pokemonRepository.countByName("Moltres", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result.intValue(), is(0));
    }

    @Test
    public void testDynamicongCountByName() {
        // Pokemon is in the database
        long result = pokemonRepository.longCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(1L));
    }

    @Test
    public void testDynamicBoxedLongCountByName() {
        // Pokemon is in the database
        Long result = pokemonRepository.boxedLongCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(1L));
    }

    @Test
    public void testDynamicIntCountByName() {
        // Pokemon is in the database
        int result = pokemonRepository.intCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(1));
    }

    @Test
    public void testDynamicBoxedIntCountByName() {
        // Pokemon is in the database
        Integer result = pokemonRepository.boxedIntCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(1));
    }

    @Test
    public void testDynamicShortCountByName() {
        // Pokemon is in the database
        short result = pokemonRepository.shortCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is((short) 1));
    }

    @Test
    public void testDynamicBoxedShortCountByName() {
        // Pokemon is in the database
        Short result = pokemonRepository.boxedShortCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is((short) 1));
    }

    @Test
    public void testDynamicByteCountByName() {
        // Pokemon is in the database
        byte result = pokemonRepository.byteCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is((byte) 1));
    }

    @Test
    public void testDynamicBoxedByteCountByName() {
        // Pokemon is in the database
        Byte result = pokemonRepository.boxedByteCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is((byte) 1));
    }

    @Test
    public void testDynamicFloatCountByName() {
        // Pokemon is in the database
        float result = pokemonRepository.floatCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(1F));
    }

    @Test
    public void testDynamicBoxedFloatCountByName() {
        // Pokemon is in the database
        Float result = pokemonRepository.boxedFloatCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(1F));
    }

    @Test
    public void testDynamicDoubleCountByName() {
        // Pokemon is in the database
        double result = pokemonRepository.doubleCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(1D));
    }

    @Test
    public void testDynamicBoxedDoubleCountByName() {
        // Pokemon is in the database
        Double result = pokemonRepository.boxedDoubleCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(1D));
    }

    @Test
    public void testDynamicBigIntegerCountByName() {
        // Pokemon is in the database
        BigInteger result = pokemonRepository.bigIntegerCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(BigInteger.valueOf(1L)));
    }

    @Test
    public void testDynamicBigDecimalCountByName() {
        // Pokemon is in the database
        BigDecimal result = pokemonRepository.bigDecimalCountByName("Snorlax", Sort.create(Order.create("name")));
        assertThat(result, is(BigDecimal.valueOf(1L)));
    }

    // Simple (JPQL) max/min/avg/sum
    // Added criteria to filter out Pokemons with ID < 100 to search in default set of records

    @Test
    public void testLongGetMaxHpByIdGreaterThan() {
        long result = pokemonRepository.longGetMaxHpByIdLessThan(100);
        assertThat(result, is(285L));
    }

    @Test
    public void testBoxedLongGetMinHpByIdGreaterThan() {
        Long result = pokemonRepository.longGetMinHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(30L));
    }

    @Test
    public void testIntGetSumHpByIdLessThan() {
        int result = pokemonRepository.intGetSumHpByIdLessThan(100);
        assertThat(result, is(2696));
    }

    @Test
    public void testBoxedIntGetMaxHpByIdLessThan() {
        Integer result = pokemonRepository.intGetMaxHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(285));
    }


    @Test
    public void testShortGetMinHpByIdLessThan() {
        short result = pokemonRepository.shortGetMinHpByIdLessThan(100);
        assertThat(result, is((short) 30));
    }

    @Test
    public void testBoxedShortGetMaxHpByIdLessThan() {
        Short result = pokemonRepository.shortGetMaxHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is((short) 285));
    }

    @Test
    public void testShortGetSumHpByIdLessThan() {
        short result = pokemonRepository.shortGetSumHpByIdLessThan(100);
        assertThat(result, is((short) 2696));
    }

    // Only min fits into byte
    @Test
    public void testByteGetMinHpByIdGreaterThan() {
        byte result = pokemonRepository.byteGetMinHpByIdLessThan(100);
        assertThat(result, is((byte) 30));
    }

    // Only min fits into byte
    @Test
    public void testBoxedByteGetMinHpByIdGreaterThan() {
        Byte result = pokemonRepository.boxedByteGetMinHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is((byte) 30));
    }

    @Test
    public void testFloatGetMaxHpByIdLessThan() {
        float result = pokemonRepository.floatGetMaxHpByIdLessThan(100);
        assertThat(result, is(285f));
    }

    @Test
    public void testFloatGetMinHpByIdLessThan() {
        Float result = pokemonRepository.floatGetMinHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(30f));
    }

    @Test
    public void testFloatGetAvgHpByIdLessThan() {
        float result = pokemonRepository.floatGetAvgHpByIdLessThan(100);
        assertThat(result, is(134.8f));
    }

    @Test
    public void testFloatGetSumHpByIdLessThan() {
        Float result = pokemonRepository.floatGetSumHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(2696f));
    }

    @Test
    public void testDoubleGetMaxHpByIdLessThan() {
        double result = pokemonRepository.doubleGetMaxHpByIdLessThan(100);
        assertThat(result, is(285d));
    }

    @Test
    public void testDoubleGetMinHpByIdLessThan() {
        Double result = pokemonRepository.doubleGetMinHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(30d));
    }

    @Test
    public void testDoubleGetAvgHpByIdLessThan() {
        double result = pokemonRepository.doubleGetAvgHpByIdLessThan(100);
        assertThat(result, is(134.8d));
    }


    @Test
    public void testDoubleGetSumHpByIdLessThan() {
        Double result = pokemonRepository.doubleGetSumHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(2696d));
    }

    @Test
    public void testBigIntegerGetMaxHpByIdLessThan() {
        BigInteger result = pokemonRepository.bigIntGetMaxHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(BigInteger.valueOf(285L)));
    }

    @Test
    public void testBigDecimalGetMinHpByIdLessThan() {
        BigDecimal result = pokemonRepository.bigDecGetMinHpByIdLessThan(100);
        assertThat(result, notNullValue());
        assertThat(result, is(BigDecimal.valueOf(30L)));
    }

    // Dynamic (criteria API) max/min/avg/sum
    // Added criteria to filter out Pokemons with ID < 100 to search in default set of records
    // Sort makes no sense in those kind of projections, but it shall work too because SQL allows it

    @Test
    public void testDynamicLongGetMaxHpByIdGreaterThan() {
        long result = pokemonRepository.longGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(285L));
    }

    @Test
    public void testDynamicBoxedLongGetMinHpByIdGreaterThan() {
        Long result = pokemonRepository.longGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(30L));
    }

    @Test
    public void testDynamicIntGetSumHpByIdLessThan() {
        int result = pokemonRepository.intGetSumHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(2696));
    }

    @Test
    public void testDynamicBoxedIntGetMaxHpByIdLessThan() {
        Integer result = pokemonRepository.intGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(285));
    }


    @Test
    public void testDynamicShortGetMinHpByIdLessThan() {
        short result = pokemonRepository.shortGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is((short) 30));
    }

    @Test
    public void testDynamicBoxedShortGetMaxHpByIdLessThan() {
        Short result = pokemonRepository.shortGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is((short) 285));
    }

    @Test
    public void testDynamicShortGetSumHpByIdLessThan() {
        short result = pokemonRepository.shortGetSumHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is((short) 2696));
    }

    // Only min fits into byte
    @Test
    public void testDynamicByteGetMinHpByIdGreaterThan() {
        byte result = pokemonRepository.byteGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is((byte) 30));
    }

    // Only min fits into byte
    @Test
    public void testDynamicBoxedByteGetMinHpByIdGreaterThan() {
        Byte result = pokemonRepository.boxedByteGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is((byte) 30));
    }

    @Test
    public void testDynamicFloatGetMaxHpByIdLessThan() {
        float result = pokemonRepository.floatGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(285f));
    }

    @Test
    public void testDynamicFloatGetMinHpByIdLessThan() {
        Float result = pokemonRepository.floatGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(30f));
    }

    @Test
    public void testDynamicFloatGetAvgHpByIdLessThan() {
        float result = pokemonRepository.floatGetAvgHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(134.8f));
    }

    @Test
    public void testDynamicFloatGetSumHpByIdLessThan() {
        Float result = pokemonRepository.floatGetSumHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(2696f));
    }

    @Test
    public void testDynamicDoubleGetMaxHpByIdLessThan() {
        double result = pokemonRepository.doubleGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(285d));
    }

    @Test
    public void testDynamicDoubleGetMinHpByIdLessThan() {
        Double result = pokemonRepository.doubleGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(30d));
    }

    @Test
    public void testDynamicDoubleGetAvgHpByIdLessThan() {
        double result = pokemonRepository.doubleGetAvgHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, is(134.8d));
    }


    @Test
    public void testDynamicDoubleGetSumHpByIdLessThan() {
        Double result = pokemonRepository.doubleGetSumHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(2696d));
    }

    @Test
    public void testDynamicBigIntegerGetMaxHpByIdLessThan() {
        BigInteger result = pokemonRepository.bigIntGetMaxHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(BigInteger.valueOf(285L)));
    }

    @Test
    public void testDynamicBigDecimalGetMinHpByIdLessThan() {
        BigDecimal result = pokemonRepository.bigDecGetMinHpByIdLessThan(100, Sort.create(Order.create("name")));
        assertThat(result, notNullValue());
        assertThat(result, is(BigDecimal.valueOf(30L)));
    }

    @BeforeAll
    public static void before(DataRegistry data) {
        pokemonRepository = data.repository(PokemonRepository.class);
    }

    @AfterAll
    public static void after() {
        pokemonRepository = null;
    }

}
