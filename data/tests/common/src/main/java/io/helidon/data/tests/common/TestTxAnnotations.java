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
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
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

public class TestTxAnnotations {

    private static final System.Logger LOGGER = System.getLogger(TestTxAnnotations.class.getName());

    private static DataRegistry data;
    private static PokemonRepository pokemonRepository;
    private static Dao dao;

    @BeforeAll
    public static void before(DataRegistry data) {
        TestTxAnnotations.data = data;
        pokemonRepository = data.repository(PokemonRepository.class);
        dao = Services.get(Dao.class);
        dao.setup(pokemonRepository);
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
                     () ->  dao.mandatoryTopLevel(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testNewTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewTopLevel");
        Pokemon pokemon = Pokemon.clone(NEW_POKEMONS.get(101));
        LOGGER.log(System.Logger.Level.INFO, " - Pokemon: " + pokemon.toString());
        pokemon = dao.newTopLevel(pokemon);
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(true));
        assertThat(dbPokemon.get(), is(pokemon));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testNeverTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverTopLevel");
        Pokemon pokemon = Pokemon.clone(NEW_POKEMONS.get(102));
        LOGGER.log(System.Logger.Level.INFO, " - Pokemon: " + pokemon.toString());
        pokemon = dao.neverTopLevel(pokemon);
        // Instance stored outside transaction context can't be found
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

    @Test
    public void testRequiredTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredTopLevel");
        Pokemon pokemon = Pokemon.clone(NEW_POKEMONS.get(103));
        LOGGER.log(System.Logger.Level.INFO, " - Pokemon: " + pokemon.toString());
        pokemon = dao.requiredTopLevel(pokemon);
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(true));
        assertThat(dbPokemon.get(), is(pokemon));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testSupportedTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedTopLevel");
        Pokemon pokemon = Pokemon.clone(NEW_POKEMONS.get(104));
        LOGGER.log(System.Logger.Level.INFO, " - Pokemon: " + pokemon.toString());
        pokemon = dao.supportedTopLevel(pokemon);
        // Instance stored outside transaction context can't be found
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

    // Instance is stored outside transaction context so persist call on EntityManager will not be synchronized to the database.
    @Test
    public void testUnsupportedTopLevel() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedTopLevel");
        Pokemon pokemon = Pokemon.clone(NEW_POKEMONS.get(105));
        LOGGER.log(System.Logger.Level.INFO, " - Pokemon: " + pokemon.toString());
        pokemon = dao.unsupportedTopLevel(pokemon);
        // Instance stored outside transaction context can't be found
        Optional<Pokemon> dbPokemon = pokemonRepository.findById(pokemon.getId());
        assertThat(dbPokemon.isPresent(), is(false));
    }

    @Test
    public void testMandatoryInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryInNew");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(104));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(105));
        Result result = dao.mandatoryInNew(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNewInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewInNew");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(106));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(107));
        Result result = dao.newInNew(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNeverInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverInNew");
        assertThrows(TxException.class,
                     () ->  dao.neverInNew(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testRequiredInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredInNew");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(106));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(107));
        Result result = dao.requiredInNew(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testSupportedInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedInNew");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(106));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(107));
        Result result = dao.supportedInNew(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testUnsupportedInNew() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedInNew");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(106));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(107));
        Result result = dao.unsupportedInNew(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testMandatoryInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryInNever");
        assertThrows(TxException.class,
                     () ->  dao.mandatoryInNever(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testNewInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewInNever");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.newInNever(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNeverInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverInNever");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.neverInNever(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testRequiredInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredInNever");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.requiredInNever(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testSupportedInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedInNever");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.supportedInNever(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testUnsupportedInNever() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedInNever");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.unsupportedInNever(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testMandatoryInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryInRequired");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.mandatoryInRequired(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNewInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewInRequired");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.newInRequired(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNeverInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverInRequired");
        assertThrows(TxException.class,
                     () ->  dao.neverInRequired(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testRequiredInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredInRequired");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.requiredInRequired(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testSupportedInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedInRequired");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.supportedInRequired(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testUnsupportedInRequired() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedInRequired");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.unsupportedInRequired(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testMandatoryInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryInSupported");
        assertThrows(TxException.class,
                     () ->  dao.mandatoryInSupported(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testNewInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewInSupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.newInSupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNeverInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverInSupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.neverInSupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testRequiredInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredInSupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.requiredInSupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testSupportedInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedInSupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.supportedInSupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testUnsupportedInSupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedInSupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.unsupportedInSupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testMandatoryInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testMandatoryInUnsupported");
        assertThrows(TxException.class,
                     () ->  dao.mandatoryInUnsupported(Pokemon.clone(NEW_POKEMONS.get(100))));
    }

    @Test
    public void testNewInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNewInUnsupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.newInUnsupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testNeverInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testNeverInUnsupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.neverInUnsupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testRequiredInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testRequiredInUnsupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.requiredInUnsupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testSupportedInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testSupportedInUnsupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.supportedInUnsupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    @Test
    public void testUnsupportedInUnsupported() {
        LOGGER.log(System.Logger.Level.INFO, "Running testUnsupportedInUnsupported");
        Pokemon firstSaved = Pokemon.clone(NEW_POKEMONS.get(108));
        Pokemon secondSaved = Pokemon.clone(NEW_POKEMONS.get(109));
        Result result = dao.unsupportedInUnsupported(firstSaved, secondSaved);
        assertThat(result.firstDb().isPresent(), is(true));
        assertThat(result.secondDb().isPresent(), is(true));
        assertThat(result.firstDb().get(), is(firstSaved));
        assertThat(result.secondDb().get(), is(secondSaved));
    }

    /**
     * Dao service with transaction annotations.
     */
    @Service.Singleton
    static class Dao {

        private PokemonRepository pokemonRepository;

        Dao() {
            this.pokemonRepository = null;
        }

        void setup(PokemonRepository pokemonRepository) {
            this.pokemonRepository = pokemonRepository;
        }

        @Tx.Mandatory
        public Pokemon mandatoryTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.New
        public Pokemon newTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.Never
        public Pokemon neverTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.Required
        public Pokemon requiredTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.Supported
        public Pokemon supportedTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.Unsupported
        public Pokemon unsupportedTopLevel(Pokemon pokemon) {
            return pokemonRepository.insert(pokemon);
        }

        @Tx.New
        public Result mandatoryInNew(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    mandatoryInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }


        @Tx.New
        public Result newInNew(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    newInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.New
        public void neverInNew(Pokemon pokemon) {
            neverInner(pokemon);
        }

        @Tx.New
        public Result requiredInNew(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    requiredInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.New
        public Result supportedInNew(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    supportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.New
        public Result unsupportedInNew(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    unsupportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Never
        public void mandatoryInNever(Pokemon pokemon) {
            mandatoryInner(pokemon);
        }

        @Tx.Never
        public Result newInNever(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    newInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Never
        public Result neverInNever(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    neverInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Never
        public Result requiredInNever(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    requiredInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Never
        public Result supportedInNever(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    supportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Never
        public Result unsupportedInNever(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    unsupportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Required
        public Result mandatoryInRequired(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    mandatoryInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Required
        public Result newInRequired(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    newInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Required
        public void neverInRequired(Pokemon pokemon) {
            neverInner(pokemon);
        }

        @Tx.Required
        public Result requiredInRequired(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    requiredInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Required
        public Result supportedInRequired(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    supportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Required
        public Result unsupportedInRequired(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    unsupportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Supported
        public void mandatoryInSupported(Pokemon pokemon) {
            mandatoryInner(pokemon);
        }

        @Tx.Supported
        public Result newInSupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    newInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Supported
        public Result neverInSupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    neverInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Supported
        public Result requiredInSupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    requiredInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Supported
        public Result supportedInSupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    supportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Supported
        public Result unsupportedInSupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    unsupportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Unsupported
        public void mandatoryInUnsupported(Pokemon pokemon) {
            mandatoryInner(pokemon);
        }

        @Tx.Unsupported
        public Result newInUnsupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    newInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Unsupported
        public Result neverInUnsupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    neverInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Unsupported
        public Result requiredInUnsupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    requiredInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Unsupported
        public Result supportedInUnsupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    supportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Unsupported
        public Result unsupportedInUnsupported(Pokemon firstSaved, Pokemon secondSaved) {
            return new Result(
                    pokemonRepository.insert(firstSaved),
                    unsupportedInner(secondSaved),
                    pokemonRepository.findById(firstSaved.getId()),
                    pokemonRepository.findById(secondSaved.getId())
            );
        }

        @Tx.Mandatory
        public Pokemon mandatoryInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

        @Tx.New
        public Pokemon newInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

        @Tx.Never
        public Pokemon neverInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

        @Tx.Required
        public Pokemon requiredInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

        @Tx.Supported
        public Pokemon supportedInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

        @Tx.Unsupported
        public Pokemon unsupportedInner(Pokemon pokemon) {
            pokemonRepository.insert(pokemon);
            return pokemon;
        }

    }

    record Result(Pokemon firstSaved,
                  Pokemon secondSaved,
                  Optional<Pokemon> firstDb,
                  Optional<Pokemon> secondDb) {
    }

}
