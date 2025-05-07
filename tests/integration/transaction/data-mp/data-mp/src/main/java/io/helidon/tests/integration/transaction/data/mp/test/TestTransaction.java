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
package io.helidon.tests.integration.transaction.data.mp.test;

import java.util.List;
import java.util.Optional;

import io.helidon.service.registry.Services;
import io.helidon.tests.integration.transaction.data.mp.model.Pokemon;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public abstract class TestTransaction {

    private static final System.Logger LOGGER = System.getLogger(TestTransaction.class.getName());

    @Inject
    protected CdiEM em;
    @Inject
    protected PokemonRepository pokemonRepository;
    protected final TxService dao;

    public TestTransaction() {
        this.dao = Services.get(TxService.class);
    }

    /**
     * Verify that persistence context configured for Helidon Data works.
     */
    @Test
    public void testDataPersistenceContext() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testDataPersistenceContext");
        Optional<Pokemon> pokemon = pokemonRepository.findById(Data.POKEMONS[1].getId());
        assertThat(pokemon.isPresent(), is(true));
        assertThat(pokemon.get(), is(Data.POKEMONS[1]));
    }

    /**
     * Verify that persistence context configured for CDI works.
     */
    @Test
    public void testCdiPersistenceContext() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testCdiPersistenceContext");
        Pokemon pokemon = em.get().createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", Data.POKEMONS[2].getId())
                .getSingleResult();
        assertThat(pokemon, is(Data.POKEMONS[2]));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transactions.
     */
    @Test
    public void testTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testTransaction");
        dao.required(pokemonRepository,
                     em,
                     TestTransaction::dataTask,
                     TestTransaction::cdiEmTask,
                     TestTransaction::dataVerification);
    }

    // testTransaction helper: Helidon Data DB insert task
    static void dataTask(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Data.NEW_POKEMONS.get(100);
        pokemonRepository.insert(pokemon);
    }

    // testTransaction helper: CDI JPA support DB insert task
    static void cdiEmTask(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(101);
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

    /**
     * Test valid Helidon Data task and failing CDI JPA support task to run under the same JTA transactions.
     */
    @Test
    public void testFailingCdiTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testFailingCdiTransaction");
        dao.required(pokemonRepository,
                     em,
                     TestTransaction::dataTaskFailingCdi,
                     TestTransaction::cdiEmTaskFailingCdi,
                     TestTransaction::dataVerificationFailingCdi);
    }

    // testTransaction helper: Helidon Data DB insert task
    static void dataTaskFailingCdi(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Data.NEW_POKEMONS.get(102);
        pokemonRepository.insert(pokemon);
    }

    // testTransaction helper: CDI JPA support DB insert task
    static void cdiEmTaskFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(103));
        em.persist(pokemon);
        // 2nd persist on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Meowth");
        pokemon.setId(1);
        em.persist(pokemon);

    }

    // testTransaction helper: Verify data stored in the database
    static void dataVerificationFailingCdi(PokemonRepository pokemonRepository, CdiEM cdiEm) {
        // Retrieve data repository record the data way
        Pokemon src = Data.NEW_POKEMONS.get(102);
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
        // Retrieve CDI record the CDI way: Task failed so there should me no record in DB
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        src = Data.NEW_POKEMONS.get(103);
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getResultList();
        assertThat(pokemons, is(empty()));
    }

    /**
     * Test valid Helidon Data task and failing CDI JPA support task to run under the same JTA transactions.
     */
    @Test
    public void testFailingDataTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testFailingDataTransaction");
        dao.required(pokemonRepository,
                     em,
                     TestTransaction::dataTaskFailingData,
                     TestTransaction::cdiEmTaskFailingData,
                     TestTransaction::dataVerificationFailingData);
    }

    // testTransaction helper: Helidon Data DB insert task
    static void dataTaskFailingData(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(104));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Raichu");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testTransaction helper: CDI JPA support DB insert task
    static void cdiEmTaskFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(105);
        em.persist(pokemon);
    }

    // testTransaction helper: Verify data stored in the database
    static void dataVerificationFailingData(PokemonRepository pokemonRepository, CdiEM cdiEm) {
        // Retrieve data repository record the data way: Task failed so there should be no record in DB
        Pokemon src = Data.NEW_POKEMONS.get(104);
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(false));
        // Retrieve CDI record the CDI way
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        src = Data.NEW_POKEMONS.get(105);
        Pokemon pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
    }

}
