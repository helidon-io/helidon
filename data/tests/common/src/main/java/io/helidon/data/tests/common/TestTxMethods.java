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

import java.util.Optional;

import io.helidon.data.DataRegistry;
import io.helidon.data.tests.model.Pokemon;
import io.helidon.data.tests.repository.PokemonRepository;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.common.InitialData.NEW_POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTxMethods {

    private static final System.Logger LOGGER = System.getLogger(TestTxMethods.class.getName());

    private static DataRegistry data;
    private static PokemonRepository pokemonRepository;

    @BeforeAll
    public static void before(DataRegistry data) {
        TestTxMethods.data = data;
        pokemonRepository = data.repository(PokemonRepository.class);
        pokemonRepository.run(InitialData::deleteTemp);
    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        pokemonRepository = null;
    }

    @BeforeEach
    public void before() {
        pokemonRepository.run(InitialData::deleteTemp);
    }

    @Test
    public void testMandatoryTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryTopLevel");
        assertThrows(TxException.class,
                     () ->  Tx.transaction(
                             Tx.Type.MANDATORY,
                             () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(100)))));
    }

    @Test
    public void testNewTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewTopLevel");
        Pokemon pokemon = Tx.transaction(Tx.Type.NEW,
                                         () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(101))));
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(true));
        assertThat(dbPokemon.get(), is(pokemon));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testNeverTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverTopLevel");
        Pokemon pokemon = Tx.transaction(Tx.Type.NEVER,
                                         () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(102))));
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

    @Test
    public void testRequiredTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredTopLevel");
        Pokemon pokemon = Tx.transaction(Tx.Type.REQUIRED,
                                         () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(103))));
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(true));
        assertThat(dbPokemon.get(), is(pokemon));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testSupportedTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedTopLevel");
        Pokemon pokemon = Tx.transaction(Tx.Type.SUPPORTED,
                                         () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(104))));
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testUnsupportedTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedTopLevel");
        Pokemon pokemon = Tx.transaction(Tx.Type.UNSUPPORTED,
                                         () -> pokemonRepository.insert(Pokemon.clone(NEW_POKEMONS.get(105))));
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

}
