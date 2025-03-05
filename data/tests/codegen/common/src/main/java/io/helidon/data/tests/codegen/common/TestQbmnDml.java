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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.data.api.DataRegistry;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.InitialData.TRAINERS;
import static io.helidon.data.tests.codegen.common.InitialData.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class TestQbmnDml {

    private static final System.Logger LOGGER = System.getLogger(TestQbmnDml.class.getName());

    private static final Map<Integer, Pokemon> DELETE_POKEMONS = deletePokemons();

    private static PokemonRepository pokemonRepository;

    // DML simple delete (JPQL)

    @Test
    public void testDeleteByNameVoid() {
        pokemonRepository.deleteByName("Pansear");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Pansear");
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedVoid() {
        Void result = pokemonRepository.boxedVoidDeleteByName("Simisear");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Simisear");
        assertThat(result, is(nullValue()));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoolean() {
        boolean result = pokemonRepository.booleanDeleteByName("Pansage");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Pansage");
        assertThat(result, is(true));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedBoolean() {
        Boolean result = pokemonRepository.boxedBooleanDeleteByName("Simisage");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Simisage");
        assertThat(result, is(notNullValue()));
        assertThat(result, is(true));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameLong() {
        long result = pokemonRepository.longDeleteByName("Purrloin");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Purrloin");
        assertThat(result, is(1L));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedLong() {
        Long result = pokemonRepository.boxedLongDeleteByName("Liepard");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Liepard");
        assertThat(result, is(notNullValue()));
        assertThat(result, is(1L));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameInt() {
        int result = pokemonRepository.intDeleteByName("Panpour");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Panpour");
        assertThat(result, is(1));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedInt() {
        Integer result = pokemonRepository.boxedIntDeleteByName("Simipour");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Simipour");
        assertThat(result, is(notNullValue()));
        assertThat(result, is(1));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameShort() {
        short result = pokemonRepository.shortDeleteByName("Munna");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Munna");
        assertThat(result, is((short) 1));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedShort() {
        Short result = pokemonRepository.boxedShortDeleteByName("Musharna");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Musharna");
        assertThat(result, is(notNullValue()));
        assertThat(result, is((short) 1));
        assertThat(maybePokemon.isPresent(), is(false));
    }


    @Test
    public void testDeleteByNameByte() {
        byte result = pokemonRepository.byteDeleteByName("Blitzle");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Blitzle");
        assertThat(result, is((byte) 1));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @Test
    public void testDeleteByNameBoxedByte() {
        Byte result = pokemonRepository.boxedByteDeleteByName("Zebstrika");
        Optional<Pokemon> maybePokemon = pokemonRepository.optionalGetByName("Zebstrika");
        assertThat(result, is(notNullValue()));
        assertThat(result, is((byte) 1));
        assertThat(maybePokemon.isPresent(), is(false));
    }

    @BeforeAll
    public static void before(DataRegistry data) {
        pokemonRepository = data.repository(PokemonRepository.class);
        DELETE_POKEMONS.keySet().forEach(key -> pokemonRepository.insert(DELETE_POKEMONS.get(key)));
    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        pokemonRepository = null;
    }

    private static Map<Integer, Pokemon> deletePokemons() {
        Map<Integer, Pokemon> deletePokemons = new HashMap<>();
        // DML simple delete (JPQL)
        deletePokemons.put(200, new Pokemon(200, TRAINERS[1], "Pansear", 54, true, List.of(TYPES[10])));
        deletePokemons.put(201, new Pokemon(201, TRAINERS[1], "Simisear", 102, true, List.of(TYPES[10])));
        deletePokemons.put(202, new Pokemon(202, TRAINERS[1], "Pansage", 54, true, List.of(TYPES[12])));
        deletePokemons.put(203, new Pokemon(203, TRAINERS[1], "Simisage", 102, true, List.of(TYPES[12])));
        deletePokemons.put(204, new Pokemon(204, TRAINERS[1], "Purrloin", 54, true, List.of(TYPES[17])));
        deletePokemons.put(205, new Pokemon(205, TRAINERS[1], "Liepard", 102, true, List.of(TYPES[17])));
        deletePokemons.put(206, new Pokemon(206, TRAINERS[1], "Panpour", 54, true, List.of(TYPES[11])));
        deletePokemons.put(207, new Pokemon(207, TRAINERS[1], "Simipour", 102, true, List.of(TYPES[11])));
        deletePokemons.put(208, new Pokemon(208, TRAINERS[1], "Munna", 54, true, List.of(TYPES[14])));
        deletePokemons.put(209, new Pokemon(209, TRAINERS[1], "Musharna", 102, true, List.of(TYPES[14])));
        deletePokemons.put(210, new Pokemon(210, TRAINERS[1], "Blitzle", 54, true, List.of(TYPES[13])));
        deletePokemons.put(211, new Pokemon(211, TRAINERS[1], "Zebstrika", 102, true, List.of(TYPES[13])));
        return Map.copyOf(deletePokemons);
    }

}
