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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.data.api.DataRegistry;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.InitialData.POKEMONS;
import static io.helidon.data.tests.codegen.common.TestUtils.checkPokemonsList;
import static io.helidon.data.tests.codegen.common.TestUtils.pokemonsList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestQueryByAnnotation {

    private static final System.Logger LOGGER = System.getLogger(TestQueryByAnnotation.class.getName());

    private static PokemonRepository pokemonRepository;

    // Return single Pokemon bv annotation

    @Test
    public void testSelectByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[1];
        Pokemon result = pokemonRepository.selectByName(pokemon.getName());
        assertThat(result, notNullValue());
        assertThat(result, is(pokemon));
    }

    @Test
    public void testGSelectMissingByName() {
        // Pokemon is not in the database, RuntimeException shall be thrown
        assertThrows(RuntimeException.class, () -> pokemonRepository.selectByName("Beedrill"));
    }

    // Return Optional<Pokemon> by annotation

    @Test
    public void testOptionalSelectByName() {
        // Pokemon is in the database
        Pokemon pokemon = POKEMONS[2];
        Optional<Pokemon> result = pokemonRepository.optionalSelectByName(pokemon.getName());
        assertThat(result.isPresent(), is(true));
        assertThat(result.get(), is(pokemon));
    }

    @Test
    public void testOptionalSelectMissingByName() {
        // Pokemon is not in the database
        Optional<Pokemon> result =  pokemonRepository.optionalSelectByName("Kakuna");
        assertThat(result.isPresent(), is(false));
    }

    // Return List<Entity> find projection

    @Test
    public void testSelectAll() {
        List<Pokemon> pokemons = pokemonRepository.selectAll();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Return Collection<Entity> find projection

    @Test
    public void testCollectionSelectAll() {
        Collection<Pokemon> pokemons = pokemonRepository.collectionSelectAll();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons, checkPokemons);
    }

    // Return Stream<Entity> find projection

    @Test
    public void testStreamSelectAll() {
        Stream<Pokemon> pokemons = pokemonRepository.streamSelectAll();
        List<Pokemon> checkPokemons = pokemonsList();
        checkPokemonsList(pokemons.toList(), checkPokemons);
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
