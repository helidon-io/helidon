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
package io.helidon.tests.integration.transaction.data.mp;

import java.util.Optional;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.testing.AddConfigSource;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.tests.integration.transaction.data.mp.model.Pokemon;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@Suite(MySqlSuite.class)
@Testcontainers(disabledWithoutDocker = true)
public class TestTransaction {

    @Inject
    private CdiEM em;
    @Inject
    private PokemonRepository pokemonRepository;
    private final Dao dao;

    TestTransaction() {
        this.dao = Services.get(Dao.class);
    }

    /**
     * Verify that persistence context configured for Helidon Data works.
     */
    @Test
    void testDataPersistenceContext() {
        Optional<Pokemon> pokemon = pokemonRepository.findById(Data.POKEMONS[1].getId());
        assertThat(pokemon.isPresent(), is(true));
        assertThat(pokemon.get(), is(Data.POKEMONS[1]));
    }

    /**
     * Verify that persistence context configured for CDI works.
     */
    @Test
    void testCdiPersistenceContext() {
        Pokemon pokemon = em.get().createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", Data.POKEMONS[2].getId())
                .getSingleResult();
        assertThat(pokemon, is(Data.POKEMONS[2]));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transactions.
     */
    @Test
    void testTransaction() {
        dao.required(pokemonRepository, em);
    }

    // testTransaction helper: Helidon Data DB insert task
    static void dataTask(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(100));
        pokemonRepository.save(pokemon);
    }

    // testTransaction helper: CDI JPA support DB insert task
    static void cdiEmTask(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(101));
        em.persist(pokemon);
    }

    // testTransaction helper: Verify data stored in the database
    static void dataVerification(PokemonRepository pokemonRepository, CdiEM cdiEm) {
        // Retrieve CDI record the CDI way
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(101);
        Pokemon pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
        // Retrieve data repository record the data way
        src = Data.NEW_POKEMONS.get(100);
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
        // Retrieve CDI record the data way
        src = Data.NEW_POKEMONS.get(101);
        maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
        // Retrieve data repository record the CDI way
        src = Data.NEW_POKEMONS.get(100);
        pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
    }

    @AddConfigSource
    static ConfigSource config() {
        return MpConfigSources.create(MySqlSuite.cdiUrlConfig());
    }

}
