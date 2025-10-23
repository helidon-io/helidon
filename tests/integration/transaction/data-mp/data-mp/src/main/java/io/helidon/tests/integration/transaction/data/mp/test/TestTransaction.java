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
import io.helidon.transaction.TxException;

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
    @Inject
    protected Dao dao;
    protected final TxService txService;

    public TestTransaction() {
        this.txService = Services.get(TxService.class);
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
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in separate transactions.
     */
    @Test
    public void testTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testTransaction");
        txService.required(pokemonRepository,
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
     * Test valid Helidon Data task and failing CDI JPA support task to run under the same JTA transaction manager.
     * CDI and data tasks run in separate transactions.
     */
    @Test
    public void testFailingCdiTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testFailingCdiTransaction");
        txService.required(pokemonRepository,
                           em,
                           TestTransaction::dataTaskFailingCdi,
                           TestTransaction::cdiEmTaskFailingCdi,
                           TestTransaction::dataVerificationFailingCdi);
    }

    // testFailingCdiTransaction helper: Helidon Data DB insert task
    static void dataTaskFailingCdi(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Data.NEW_POKEMONS.get(102);
        pokemonRepository.insert(pokemon);
    }

    // testFailingCdiTransaction helper: CDI JPA support DB insert task
    static void cdiEmTaskFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(103));
        em.persist(pokemon);
        // 2nd persist on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Meowth");
        pokemon.setId(1);
        em.persist(pokemon);

    }

    // testFailingCdiTransaction helper: Verify data stored in the database
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
     * Test valid Helidon Data task and failing CDI JPA support task to run under the same JTA transaction manager.
     * CDI and data tasks run in separate transactions.
     */
    @Test
    public void testFailingDataTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testFailingDataTransaction");
        txService.required(pokemonRepository,
                           em,
                           TestTransaction::dataTaskFailingData,
                           TestTransaction::cdiEmTaskFailingData,
                           TestTransaction::dataVerificationFailingData);
    }

    // testFailingDataTransaction helper: Helidon Data DB insert task
    static void dataTaskFailingData(PokemonRepository pokemonRepository) {
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(104));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Raichu");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testFailingDataTransaction helper: CDI JPA support DB insert task
    static void cdiEmTaskFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(105);
        em.persist(pokemon);
    }

    // testFailingDataTransaction helper: Verify data stored in the database
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

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleTransaction");
        txService.requiredDataFirst(pokemonRepository,
                                    em,
                                    TestTransaction::dataSingleTask,
                                    TestTransaction::cdiEmSingleTask);
        txService.requiredDataFirst(pokemonRepository,
                                    em,
                                    TestTransaction::dataSingleVerification,
                                    TestTransaction::cdiSingleVerification);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleTask(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Data.NEW_POKEMONS.get(106);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleTask(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(107);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleVerification(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Data.NEW_POKEMONS.get(106);
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
        // Retrieve CDI record the data way
        src = Data.NEW_POKEMONS.get(107);
        maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleVerification(CdiEM cdiEm) {
        // Retrieve CDI record the CDI way
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(107);
        Pokemon pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
        // Retrieve data repository record the CDI way
        src = Data.NEW_POKEMONS.get(106);
        pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleTransactionFailingData() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleTransactionFailingData");
        try {
            txService.requiredCdiFirst(pokemonRepository,
                                       em,
                                       TestTransaction::dataSingleTaskFailingData,
                                       TestTransaction::cdiEmSingleTaskFailingData);
        } catch (TxException e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("TxException in data task: %s", e.getMessage()));
        }
        txService.requiredCdiFirst(pokemonRepository,
                                   em,
                                   TestTransaction::dataSingleVerificationFailingData,
                                   TestTransaction::cdiSingleVerificationFailingData);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleTaskFailingData(PokemonRepository pokemonRepository) {
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(108));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Persian");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleTaskFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(109);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleVerificationFailingData(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Pokemon.clone(Data.NEW_POKEMONS.get(108));
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(false));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleVerificationFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(109);
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getResultList();
        assertThat(pokemons, is(empty()));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleTransactionFailingCdi() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleTransactionFailingCdi");
        try {
            txService.requiredDataFirst(pokemonRepository,
                                        em,
                                        TestTransaction::dataSingleTaskFailingCdi,
                                        TestTransaction::cdiEmSingleTaskFailingCdi);
        } catch (TxException e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("TxException in data task: %s", e.getMessage()));
        }
        txService.requiredDataFirst(pokemonRepository,
                                    em,
                                    TestTransaction::dataSingleVerificationFailingCdi,
                                    TestTransaction::cdiSingleVerificationFailingCdi);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleTaskFailingCdi(PokemonRepository pokemonRepository) {
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(110));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Persian");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleTaskFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(111);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleVerificationFailingCdi(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Pokemon.clone(Data.NEW_POKEMONS.get(110));
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(false));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleVerificationFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(111);
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getResultList();
        assertThat(pokemons, is(empty()));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleCdiTransaction() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleCdiTransaction");
        dao.requiredDataFirst(pokemonRepository,
                              em,
                              TestTransaction::dataSingleDaoTask,
                              TestTransaction::cdiEmSingleDaoTask);
        dao.requiredDataFirst(pokemonRepository,
                              em,
                              TestTransaction::dataSingleDaoVerification,
                              TestTransaction::cdiSingleDaoVerification);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleDaoTask(PokemonRepository pokemonRepository) {
        Pokemon pokemon = Data.NEW_POKEMONS.get(112);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleDaoTask(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(113);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleDaoVerification(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Data.NEW_POKEMONS.get(112);
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
        // Retrieve CDI record the data way
        src = Data.NEW_POKEMONS.get(113);
        maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(true));
        assertThat(maybePokemon.get(), is(src));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleDaoVerification(CdiEM cdiEm) {
        // Retrieve CDI record the CDI way
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(113);
        Pokemon pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
        // Retrieve data repository record the CDI way
        src = Data.NEW_POKEMONS.get(112);
        pokemon = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getSingleResult();
        assertThat(pokemon, is(src));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleCdiTransactionFailingData() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleCdiTransactionFailingData");
        try {
            dao.requiredCdiFirst(pokemonRepository,
                                       em,
                                       TestTransaction::dataSingleDaoTaskFailingData,
                                       TestTransaction::cdiEmSingleDaoTaskFailingData);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("%s in data task: %s",
                                                                e.getClass().getSimpleName(),
                                                                e.getMessage()));
        }
        dao.requiredCdiFirst(pokemonRepository,
                                   em,
                                   TestTransaction::dataSingleDaoVerificationFailingData,
                                   TestTransaction::cdiSingleDaoVerificationFailingData);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleDaoTaskFailingData(PokemonRepository pokemonRepository) {
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(114));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Dugtrio");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleDaoTaskFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(115);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleDaoVerificationFailingData(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Pokemon.clone(Data.NEW_POKEMONS.get(114));
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(false));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleDaoVerificationFailingData(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(115);
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getResultList();
        assertThat(pokemons, is(empty()));
    }

    /**
     * Test both Helidon Data and CDI JPA support to run under the same JTA transaction manager.
     * CDI and data tasks run in single transaction.
     */
    @Test
    public void testSingleCdiTransactionFailingCdi() {
        LOGGER.log(System.Logger.Level.DEBUG, "Running testSingleCdiTransactionFailingCdi");
        try {
            dao.requiredDataFirst(pokemonRepository,
                                  em,
                                  TestTransaction::dataSingleDaoTaskFailingCdi,
                                  TestTransaction::cdiEmSingleDaoTaskFailingCdi);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.DEBUG, String.format("%s in data task: %s",
                                                                e.getClass().getSimpleName(),
                                                                e.getMessage()));
        }
        dao.requiredDataFirst(pokemonRepository,
                              em,
                              TestTransaction::dataSingleDaoVerificationFailingCdi,
                              TestTransaction::cdiSingleDaoVerificationFailingCdi);
    }

    // testSingleTransaction helper: Helidon Data DB insert task
    static void dataSingleDaoTaskFailingCdi(PokemonRepository pokemonRepository) {
        // Use clone copy to not propagate ID change to source instance
        Pokemon pokemon = Pokemon.clone(Data.NEW_POKEMONS.get(116));
        pokemonRepository.insert(pokemon);
        // 2nd insert on the same entity throws an exception: ID collides with existing ROW in DB
        pokemon.setName("Persian");
        pokemon.setId(1);
        pokemonRepository.insert(pokemon);
    }

    // testSingleTransaction helper: CDI JPA support DB insert task
    static void cdiEmSingleDaoTaskFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon pokemon = Data.NEW_POKEMONS.get(117);
        em.persist(pokemon);
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void dataSingleDaoVerificationFailingCdi(PokemonRepository pokemonRepository) {
        // Retrieve data repository record the data way
        Pokemon src = Pokemon.clone(Data.NEW_POKEMONS.get(116));
        Optional<Pokemon> maybePokemon = pokemonRepository.findById(src.getId());
        assertThat(maybePokemon.isPresent(), is(false));
    }

    // testSingleTransaction helper: Verify data stored in the database
    static void cdiSingleDaoVerificationFailingCdi(CdiEM cdiEm) {
        EntityManager em = cdiEm.get();
        em.joinTransaction();
        Pokemon src = Data.NEW_POKEMONS.get(117);
        List<Pokemon> pokemons = em.createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", src.getId())
                .getResultList();
        assertThat(pokemons, is(empty()));
    }

}
