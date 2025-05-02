package io.helidon.tests.integration.transaction.data.mp;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

import io.helidon.config.ConfigSources;
import io.helidon.data.DataRegistry;
import io.helidon.microprofile.testing.Configuration;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.testing.junit5.Testing;
import io.helidon.testing.junit5.suite.Suite;
import io.helidon.tests.integration.transaction.data.mp.model.Pokemon;
import io.helidon.tests.integration.transaction.data.mp.repository.PokemonRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@HelidonTest
@Configuration(configSources = {MySqlSuite.CONFIG_FILE})
@AddBean(TestTransaction.Dao.class)
@Suite(MySqlSuite.class)
@Testcontainers(disabledWithoutDocker = true)
public class TestTransaction {

    private static final System.Logger LOGGER = System.getLogger(TestTransaction.class.getName());

    @Inject
    private Dao dao;
    private final PokemonRepository pokemonRepository;

    TestTransaction(PokemonRepository pokemonRepository) {
        this.pokemonRepository = pokemonRepository;
    }
/*
    @BeforeAll
    public static void before(DataRegistry data) {
        LOGGER.log(System.Logger.Level.INFO, "Running TestTransaction.before()");
        pokemonRepository = data.repository(PokemonRepository.class);
    }

    @AfterAll
    public static void after() {
        LOGGER.log(System.Logger.Level.INFO, "Running TestTransaction.after()");
        pokemonRepository = null;
    }
*/
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
    //@Test
    void testCdiPersistenceContext() {
        //Dao dao = CDI.current().select(Dao.class).get();
        Pokemon pokemon = dao.em().createQuery("SELECT p FROM Pokemon p WHERE p.id = :id", Pokemon.class)
                .setParameter("id", Data.POKEMONS[2].getId())
                .getSingleResult();
        assertThat(pokemon, is(Data.POKEMONS[2]));
    }

    // CDI DAO class
    @ApplicationScoped
    static class Dao {

        @PersistenceContext(unitName = "cdi-test")
        EntityManager em;

        EntityManager em() {
            return em;
        }

    }

}
