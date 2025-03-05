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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.data.DataRegistry;
import io.helidon.data.tests.codegen.model.Pokemon;
import io.helidon.data.tests.codegen.repository.PokemonRepository;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.data.tests.codegen.common.InitialData.NEW_POKEMONS;
import static io.helidon.data.tests.codegen.common.InitialData.POKEMONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestTransaction {

    private static final Logger LOGGER = System.getLogger(TestTransaction.class.getName());

    private static DataRegistry data;
    private static PokemonRepository pokemonRepository;

    // Calling top level transaction of tye Tx.Type.MANDATORY shall throw an exception
    @Test
    public void testMandatoryAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testMandatoryAutomaticTopLevel");
        assertThrows(TxException.class,
                     () -> data.transaction(Tx.Type.MANDATORY,
                                            () -> pokemonRepository.findById(POKEMONS[1].getId())));
    }

    @Test
    public void testNewAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNewAutomaticTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.NEW,
                                                           () -> pokemonRepository.findById(POKEMONS[1].getId()));
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testNeverAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNeverAutomaticTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.NEVER,
                                                           () -> pokemonRepository.findById(POKEMONS[1].getId()));
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testRequiredAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testRequiredAutomaticTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.REQUIRED,
                                                           () -> pokemonRepository.findById(POKEMONS[1].getId()));
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testSupportedAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testSupportedAutomaticTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.SUPPORTED,
                                                           () -> pokemonRepository.findById(POKEMONS[1].getId()));
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testUnsupportedAutomaticTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testUnsupportedAutomaticTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.UNSUPPORTED,
                                                           () -> pokemonRepository.findById(POKEMONS[1].getId()));
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    // Calling top level transaction of tye Tx.Type.MANDATORY shall throw an exception
    @Test
    public void testMandatoryManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testMandatoryManualTopLevel");
        assertThrows(TxException.class,
                     () -> data.transaction(Tx.Type.MANDATORY,
                                            tx -> {
                                                try {
                                                    Optional<Pokemon> critter =
                                                            pokemonRepository.findById(POKEMONS[1].getId());
                                                    tx.commit();
                                                    return critter;
                                                } catch (RuntimeException ex) {
                                                    tx.rollback();
                                                    throw ex;
                                                }
                                            }));
    }

    @Test
    public void testNewManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNewManualTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.NEW,
                                                           tx -> {
                                                               try {
                                                                   Optional<Pokemon> critter = pokemonRepository.findById(
                                                                           POKEMONS[1].getId());
                                                                   tx.commit();
                                                                   return critter;
                                                               } catch (RuntimeException ex) {
                                                                   tx.rollback();
                                                                   throw ex;
                                                               }
                                                           });
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testNeverManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNeverManualTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.NEVER,
                                                           tx -> {
                                                               try {
                                                                   Optional<Pokemon> critter = pokemonRepository.findById(
                                                                           POKEMONS[1].getId());
                                                                   tx.commit();
                                                                   return critter;
                                                               } catch (RuntimeException ex) {
                                                                   tx.rollback();
                                                                   throw ex;
                                                               }
                                                           });
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testRequiredManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testRequiredManualTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.REQUIRED,
                                                           tx -> {
                                                               try {
                                                                   Optional<Pokemon> critter = pokemonRepository.findById(
                                                                           POKEMONS[1].getId());
                                                                   tx.commit();
                                                                   return critter;
                                                               } catch (RuntimeException ex) {
                                                                   tx.rollback();
                                                                   throw ex;
                                                               }
                                                           });
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testSupportedManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testSupportedManualTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.SUPPORTED,
                                                           tx -> {
                                                               try {
                                                                   Optional<Pokemon> critter = pokemonRepository.findById(
                                                                           POKEMONS[1].getId());
                                                                   tx.commit();
                                                                   return critter;
                                                               } catch (RuntimeException ex) {
                                                                   tx.rollback();
                                                                   throw ex;
                                                               }
                                                           });
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testUnsupportedManualTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testUnsupportedManualTopLevel");
        Optional<Pokemon> critterFromDb = data.transaction(Tx.Type.UNSUPPORTED,
                                                           tx -> {
                                                               try {
                                                                   Optional<Pokemon> critter = pokemonRepository.findById(
                                                                           POKEMONS[1].getId());
                                                                   tx.commit();
                                                                   return critter;
                                                               } catch (RuntimeException ex) {
                                                                   tx.rollback();
                                                                   throw ex;
                                                               }
                                                           });
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    // Calling top level transaction of tye Tx.Type.MANDATORY shall throw an exception
    @Test
    public void testMandatoryUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testMandatoryUserTopLevel");
        assertThrows(TxException.class, () -> data.transaction(Tx.Type.MANDATORY));
    }

    @Test
    public void testNewUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNewUserTopLevel");
        Tx.Transaction tx = data.transaction(Tx.Type.NEW);
        Optional<Pokemon> critterFromDb;
        try {
            critterFromDb = pokemonRepository.findById(POKEMONS[1].getId());
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testNeverUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testNeverUserTopLevel");
        Tx.Transaction tx = data.transaction(Tx.Type.NEVER);
        Optional<Pokemon> critterFromDb;
        try {
            critterFromDb = pokemonRepository.findById(POKEMONS[1].getId());
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testRequiredUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testRequiredUserTopLevel");
        Tx.Transaction tx = data.transaction(Tx.Type.REQUIRED);
        Optional<Pokemon> critterFromDb;
        try {
            critterFromDb = pokemonRepository.findById(POKEMONS[1].getId());
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testSupportedUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testSupportedUserTopLevel");
        Tx.Transaction tx = data.transaction(Tx.Type.SUPPORTED);
        Optional<Pokemon> critterFromDb;
        try {
            critterFromDb = pokemonRepository.findById(POKEMONS[1].getId());
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    @Test
    public void testUnsupportedUserTopLevel() {
        LOGGER.log(Level.INFO, () -> "Running testUnsupportedUserTopLevel");
        Tx.Transaction tx = data.transaction(Tx.Type.UNSUPPORTED);
        Optional<Pokemon> critterFromDb;
        try {
            critterFromDb = pokemonRepository.findById(POKEMONS[1].getId());
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
        assertThat(critterFromDb.isPresent(), is(true));
        critterFromDb.ifPresent(critter -> {
            assertThat(critter, is(POKEMONS[1]));
        });
    }

    // Tx.Type.MANDATORY will not throw an exception only when running with NEW and REQUIRED Tx.Type on entry level.
    // Those types guarantee transaction to be started.
    // NEVER, SUPPORTED and UNSUPPORTED Tx.Type will start entry level transaction call outside a transaction context
    // so subsequent call with Tx.Type.MANDATORY must fail.
    @Test
    public void testAutomaticMandatory2ndLevel() {
        testAutomatic2ndLevelActive("testAutomaticMandatory2ndLevel",
                                    Tx.Type.MANDATORY,
                                    List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
        testAutomatic2ndLevelMustFail("testAutomaticMandatory2ndLevel",
                                      Tx.Type.MANDATORY,
                                      List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.NEW will always start a new transaction so this test must pass for all supported entry level Tx.Type values.
    @Test
    public void testAutomaticNew2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testAutomatic2ndLevelActive("testAutomaticNew2ndLevel",
                                    Tx.Type.NEW,
                                    entryTypes);
    }

    // Tx.Type.NEVER will not throw an exception only when running with NEVER, SUPPORTED and UNSUPPORTED Tx.Type on entry level.
    // Those types guarantee entry level transaction call outside a transaction context.
    // NEW and REQUIRED Tx.Type will start entry level transaction call in transaction context so subsequent
    // call with Tx.Type.MANDATORY must fail.
    @Test
    public void testAutomaticNever2ndLevel() {
        testAutomatic2ndLevelInactive("testAutomaticNever2ndLevel",
                                    Tx.Type.NEVER,
                                    List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
        testAutomatic2ndLevelMustFail("testAutomaticNever2ndLevel",
                                      Tx.Type.NEVER,
                                      List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
    }

    // Tx.Type.REQUIRED will start a new transaction when running outside a transaction context so this test must pass for all
    // supported entry level Tx.Type values.
    @Test
    public void testAutomaticRequired2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testAutomatic2ndLevelActive("testAutomaticRequired2ndLevel",
                                    Tx.Type.REQUIRED,
                                    entryTypes);
    }

    // Tx.Type.SUPPORTED will keep transaction context from previous level. This test must pass for all supported entry level
    // Tx.Type values. But test evaluation depends on whether running inside a transaction context or not.
    @Test
    public void testAutomaticSupported2ndLevel() {
        testAutomatic2ndLevelActive("testAutomaticSupported2ndLevel",
                                      Tx.Type.SUPPORTED,
                                      List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
        testAutomatic2ndLevelInactive("testAutomaticSupported2ndLevel",
                                      Tx.Type.SUPPORTED,
                                      List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.UNSUPPORTED will suspend active transaction context when exists so this test must pass for all supported
    // entry level Tx.Type values.
    @Test
    public void testAutomaticUnsupported2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testAutomatic2ndLevelInactive("testAutomaticSupported2ndLevel",
                                      Tx.Type.SUPPORTED,
                                      entryTypes);
    }

    // Tx.Type.MANDATORY will not throw an exception only when running with NEW and REQUIRED Tx.Type on entry level.
    // Those types guarantee transaction to be started.
    // NEVER, SUPPORTED and UNSUPPORTED Tx.Type will start entry level transaction call outside a transaction context
    // so subsequent call with Tx.Type.MANDATORY must fail.
    @Test
    public void testManualMandatory2ndLevel() {
        testManual2ndLevelActive("testManualMandatory2ndLevel",
                                    Tx.Type.MANDATORY,
                                    List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
        testManual2ndLevelMustFail("testManualMandatory2ndLevel",
                                      Tx.Type.MANDATORY,
                                      List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.NEW will always start a new transaction so this test must pass for all supported entry level Tx.Type values.
    @Test
    public void testManualNew2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testManual2ndLevelActive("testManualNew2ndLevel",
                                    Tx.Type.NEW,
                                    entryTypes);
    }

    // Tx.Type.NEVER will not throw an exception only when running with NEVER, SUPPORTED and UNSUPPORTED Tx.Type on entry level.
    // Those types guarantee entry level transaction call outside a transaction context.
    // NEW and REQUIRED Tx.Type will start entry level transaction call in transaction context so subsequent
    // call with Tx.Type.MANDATORY must fail.
    @Test
    public void testManualNever2ndLevel() {
        testManual2ndLevelInactive("testManualNever2ndLevel",
                                      Tx.Type.NEVER,
                                      List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
        testManual2ndLevelMustFail("testManualNever2ndLevel",
                                      Tx.Type.NEVER,
                                      List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
    }

    // Tx.Type.REQUIRED will start a new transaction when running outside a transaction context so this test must pass for all
    // supported entry level Tx.Type values.
    @Test
    public void testManualRequired2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testManual2ndLevelActive("testManualRequired2ndLevel",
                                    Tx.Type.REQUIRED,
                                    entryTypes);
    }

    // Tx.Type.SUPPORTED will keep transaction context from previous level. This test must pass for all supported entry level
    // Tx.Type values. But test evaluation depends on whether running inside a transaction context or not.
    @Test
    public void testManualSupported2ndLevel() {
        testManual2ndLevelActive("testManualSupported2ndLevel",
                                    Tx.Type.SUPPORTED,
                                    List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
        testManual2ndLevelInactive("testManualSupported2ndLevel",
                                      Tx.Type.SUPPORTED,
                                      List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.UNSUPPORTED will suspend active transaction context when exists so this test must pass for all supported
    // entry level Tx.Type values.
    @Test
    public void testManualUnsupported2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testManual2ndLevelInactive("testManualUnsupported2ndLevel",
                                      Tx.Type.SUPPORTED,
                                      entryTypes);
    }

    // Tx.Type.MANDATORY will not throw an exception only when running with NEW and REQUIRED Tx.Type on entry level.
    // Those types guarantee transaction to be started.
    // NEVER, SUPPORTED and UNSUPPORTED Tx.Type will start entry level transaction call outside a transaction context
    // so subsequent call with Tx.Type.MANDATORY must fail.
    @Test
    public void testUserMandatory2ndLevel() {
        testUser2ndLevelActive("testUserMandatory2ndLevel",
                               Tx.Type.MANDATORY,
                               List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
            testUser2ndLevelMustFail("testUserMandatory2ndLevel",
                                       Tx.Type.MANDATORY,
                                       List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.NEW will always start a new transaction so this test must pass for all supported entry level Tx.Type values.
    @Test
    public void testUserNew2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testUser2ndLevelActive("testUserNew2ndLevel",
                               Tx.Type.NEW,
                               entryTypes);
    }

    // Tx.Type.NEVER will not throw an exception only when running with NEVER, SUPPORTED and UNSUPPORTED Tx.Type on entry level.
    // Those types guarantee entry level transaction call outside a transaction context.
    // NEW and REQUIRED Tx.Type will start entry level transaction call in transaction context so subsequent
    // call with Tx.Type.MANDATORY must fail.
    @Test
    public void testUserNever2ndLevel() {
        testUser2ndLevelInactive("testUserNever2ndLevel",
                                   Tx.Type.NEVER,
                                   List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
        testUser2ndLevelMustFail("testUserNever2ndLevel",
                                   Tx.Type.NEVER,
                                   List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
    }

    // Tx.Type.REQUIRED will start a new transaction when running outside a transaction context so this test must pass for all
    // supported entry level Tx.Type values.
    @Test
    public void testUserRequired2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testUser2ndLevelActive("testUserRequired2ndLevel",
                                 Tx.Type.REQUIRED,
                                 entryTypes);
    }

    // Tx.Type.SUPPORTED will keep transaction context from previous level. This test must pass for all supported entry level
    // Tx.Type values. But test evaluation depends on whether running inside a transaction context or not.
    @Test
    public void testUserSupported2ndLevel() {
        testUser2ndLevelActive("testUserSupported2ndLevel",
                                 Tx.Type.SUPPORTED,
                                 List.of(Tx.Type.NEW, Tx.Type.REQUIRED));
        testManual2ndLevelInactive("testUserSupported2ndLevel",
                                   Tx.Type.SUPPORTED,
                                   List.of(Tx.Type.NEVER, Tx.Type.SUPPORTED, Tx.Type.UNSUPPORTED));
    }

    // Tx.Type.UNSUPPORTED will suspend active transaction context when exists so this test must pass for all supported
    // entry level Tx.Type values.
    @Test
    public void testUserUnsupported2ndLevel() {
        List<Tx.Type> entryTypes = Arrays.stream(Tx.Type.values())
                .filter(type -> type != Tx.Type.MANDATORY)
                .toList();
        testUser2ndLevelInactive("testUserUnsupported2ndLevel",
                                   Tx.Type.SUPPORTED,
                                   entryTypes);
    }

    @BeforeAll
    public static void before(DataRegistry data) {
        TestTransaction.data = data;
        pokemonRepository = data.repository(PokemonRepository.class);
    }

    @AfterAll
    public static void after() {
        pokemonRepository.run(InitialData::deleteTemp);
        pokemonRepository = null;
    }

    // Test 2nd level transaction call type which is expected to fail with TxException
    private void testAutomatic2ndLevelMustFail(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon snorlax = NEW_POKEMONS.get(106);
        Pokemon charizard = NEW_POKEMONS.get(107);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            Optional<Pokemon> snorlaxFromDb;
            try {
                snorlaxFromDb = data.transaction(entryType, () -> {
                    pokemonRepository.save(snorlax);
                    // 2nd level call must fail
                    assertThrows(TxException.class,
                                 () -> data.transaction(txType, () -> {
                                     pokemonRepository.save(charizard);
                                 }));
                    return pokemonRepository.findById(snorlax.getId());
                });
                // But entry level transaction must still return valid result.
                assertThat(snorlaxFromDb.isPresent(), is(true));
                assertThat(snorlaxFromDb.get(), is(snorlax));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(snorlax.getId());
                pokemonRepository.deleteById(charizard.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which is expected to fail with TxException
    private void testManual2ndLevelMustFail(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon pikachu = NEW_POKEMONS.get(104);
        Pokemon machop = NEW_POKEMONS.get(105);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                Optional<Pokemon> pikachuFromDb = data.transaction(entryType, tx1 -> {
                    try {
                        pokemonRepository.save(pikachu);
                        Optional<Pokemon> dbPikachu = pokemonRepository.findById(pikachu.getId());
                        // 2nd level call must fail
                        assertThrows(TxException.class, () -> data.transaction(txType, tx2 -> {
                            try {
                                pokemonRepository.save(
                                        machop);
                                tx2.commit();
                            } catch (
                                    RuntimeException ex) {
                                tx2.rollback();
                                throw ex;
                            }
                        }));
                        tx1.commit();
                        return dbPikachu;
                    } catch (RuntimeException ex) {
                        tx1.rollback();
                        throw ex;
                    }
                });
                // But entry level transaction must still return valid result.
                assertThat(pikachuFromDb.isPresent(), is(true));
                assertThat(pikachuFromDb.get(), is(pikachu));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(pikachu.getId());
                pokemonRepository.deleteById(machop.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which is expected to fail with TxException
    private void testUser2ndLevelMustFail(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon meowth = NEW_POKEMONS.get(108);
        Pokemon magikarp = NEW_POKEMONS.get(109);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                Tx.Transaction tx1 = data.transaction(entryType);
                Optional<Pokemon> meowthFromDb;
                try {
                    pokemonRepository.save(meowth);
                    meowthFromDb = pokemonRepository.findById(meowth.getId());
                    // 2nd level call must fail
                    assertThrows(TxException.class, () -> data.transaction(txType));
                    tx1.commit();
                } catch (RuntimeException ex) {
                    tx1.rollback();
                    throw ex;
                }
                // But entry level transaction must still return valid result.
                assertThat(meowthFromDb.isPresent(), is(true));
                assertThat(meowthFromDb.get(), is(meowth));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(meowth.getId());
                pokemonRepository.deleteById(magikarp.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs in active transaction.
    // Having active transaction means, that it's content will be available to other EntityManager
    // instances after this transaction finishes.
    private void testAutomatic2ndLevelActive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon snorlax = NEW_POKEMONS.get(106);
        Pokemon charizard = NEW_POKEMONS.get(107);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            Optional<Pokemon> snorlaxFromDb;
            try {
                snorlaxFromDb = data.transaction(entryType, () -> {
                    pokemonRepository.save(snorlax);
                    // 2nd level Tx.Type
                    data.transaction(txType, () -> {
                        pokemonRepository.save(charizard);
                    });
                    return pokemonRepository.findById(snorlax.getId());
                });
                Optional<Pokemon> charizardFromDb = pokemonRepository.findById(charizard.getId());
                assertThat(snorlaxFromDb.isPresent(), is(true));
                assertThat(charizardFromDb.isPresent(), is(true));
                assertThat(snorlaxFromDb.get(), is(snorlax));
                assertThat(charizardFromDb.get(), is(charizard));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(snorlax.getId());
                pokemonRepository.deleteById(charizard.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs in active transaction.
    // Having active transaction means, that it's content will be available to other EntityManager
    // instances after this transaction finishes.
    private void testManual2ndLevelActive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon pikachu = NEW_POKEMONS.get(104);
        Pokemon machop = NEW_POKEMONS.get(105);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                Optional<Pokemon> pikachuFromDb = data.transaction(entryType, tx1 -> {
                    try {
                        pokemonRepository.save(pikachu);
                        Optional<Pokemon> dbPikachu = pokemonRepository.findById(pikachu.getId());
                        data.transaction(txType, tx2 -> {
                            try {
                                pokemonRepository.save(machop);
                                tx2.commit();
                            } catch (RuntimeException ex) {
                                tx2.rollback();
                                throw ex;
                            }
                        });
                        tx1.commit();
                        return dbPikachu;
                    } catch (RuntimeException ex) {
                        tx1.rollback();
                        throw ex;
                    }
                });
                Optional<Pokemon> machopFromDb = pokemonRepository.findById(machop.getId());
                assertThat(pikachuFromDb.isPresent(), is(true));
                assertThat(machopFromDb.isPresent(), is(true));
                assertThat(pikachuFromDb.get(), is(pikachu));
                assertThat(machopFromDb.get(), is(machop));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(pikachu.getId());
                pokemonRepository.deleteById(machop.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs in active transaction.
    // Having active transaction means, that it's content will be available to other EntityManager
    // instances after this transaction finishes.
    private void testUser2ndLevelActive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon meowth = NEW_POKEMONS.get(108);
        Pokemon magikarp = NEW_POKEMONS.get(109);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                Tx.Transaction tx1 = data.transaction(entryType);
                Optional<Pokemon> meowthFromDb;
                try {
                    pokemonRepository.save(meowth);
                    meowthFromDb = pokemonRepository.findById(meowth.getId());
                    Tx.Transaction tx2 = data.transaction(txType);
                    try {
                        pokemonRepository.save(magikarp);
                        tx2.commit();
                    } catch (RuntimeException ex) {
                        tx2.rollback();
                        throw ex;
                    }
                    tx1.commit();
                } catch (RuntimeException ex) {
                    tx1.rollback();
                    throw ex;
                }
                Optional<Pokemon> magikarpFromDb = pokemonRepository.findById(magikarp.getId());
                assertThat(meowthFromDb.isPresent(), is(true));
                assertThat(magikarpFromDb.isPresent(), is(true));
                assertThat(meowthFromDb.get(), is(meowth));
                assertThat(magikarpFromDb.get(), is(magikarp));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(meowth.getId());
                pokemonRepository.deleteById(magikarp.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs outside active transaction.
    // Having no active transaction means, that it's content may not be available to other EntityManager
    // instances after this transaction finishes so result must be retrieved as part of the transaction task.
    private void testAutomatic2ndLevelInactive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon snorlax = NEW_POKEMONS.get(106);
        Pokemon charizard = NEW_POKEMONS.get(107);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            Optional<Pokemon> snorlaxFromDb;
            AtomicReference<Optional<Pokemon>> charizardFromDbRef = new AtomicReference<>();
            try {
                snorlaxFromDb = data.transaction(entryType, () -> {
                    pokemonRepository.save(snorlax);
                    // 2nd level Tx.Type
                    data.transaction(txType, () -> {
                        pokemonRepository.save(charizard);
                        charizardFromDbRef.set(pokemonRepository.findById(charizard.getId()));
                    });
                    return pokemonRepository.findById(snorlax.getId());
                });
                Optional<Pokemon> charizardFromDb = charizardFromDbRef.get();
                assertThat(snorlaxFromDb.isPresent(), is(true));
                assertThat(charizardFromDb.isPresent(), is(true));
                assertThat(snorlaxFromDb.get(), is(snorlax));
                assertThat(charizardFromDb.get(), is(charizard));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(snorlax.getId());
                pokemonRepository.deleteById(charizard.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs outside active transaction.
    // Having no active transaction means, that it's content may not be available to other EntityManager
    // instances after this transaction finishes so result must be retrieved as part of the transaction task.
    private void testManual2ndLevelInactive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon pikachu = NEW_POKEMONS.get(104);
        Pokemon machop = NEW_POKEMONS.get(105);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                AtomicReference<Optional<Pokemon>> machopFromDbRef = new AtomicReference<>();
                Optional<Pokemon> pikachuFromDb = data.transaction(entryType, tx1 -> {
                    try {
                        pokemonRepository.save(pikachu);
                        Optional<Pokemon> dbPikachu = pokemonRepository.findById(pikachu.getId());
                        data.transaction(txType, tx2 -> {
                            try {
                                pokemonRepository.save(machop);
                                machopFromDbRef.set(pokemonRepository.findById(machop.getId()));
                                tx2.commit();
                            } catch (RuntimeException ex) {
                                tx2.rollback();
                                throw ex;
                            }
                        });
                        tx1.commit();
                        return dbPikachu;
                    } catch (RuntimeException ex) {
                        tx1.rollback();
                        throw ex;
                    }
                });
                Optional<Pokemon> machopFromDb = machopFromDbRef.get();
                assertThat(pikachuFromDb.isPresent(), is(true));
                assertThat(machopFromDb.isPresent(), is(true));
                assertThat(pikachuFromDb.get(), is(pikachu));
                assertThat(machopFromDb.get(), is(machop));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(pikachu.getId());
                pokemonRepository.deleteById(machop.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    // Test 2nd level transaction call type which runs outside active transaction.
    // Having no active transaction means, that it's content may not be available to other EntityManager
    // instances after this transaction finishes so result must be retrieved as part of the transaction task.
    private void testUser2ndLevelInactive(String testName, Tx.Type txType, List<Tx.Type> entryTypes) {
        LOGGER.log(Level.INFO, () -> String.format("Running %s", testName));
        Pokemon meowth = NEW_POKEMONS.get(108);
        Pokemon magikarp = NEW_POKEMONS.get(109);
        List<TestError> errors = new ArrayList<>();
        for (Tx.Type entryType : entryTypes) {
            LOGGER.log(Level.INFO, () -> String.format("%s :: %s", testName, entryType.name()));
            try {
                Tx.Transaction tx1 = data.transaction(entryType);
                Optional<Pokemon> meowthFromDb;
                Optional<Pokemon> magikarpFromDb;
                try {
                    pokemonRepository.save(meowth);
                    meowthFromDb = pokemonRepository.findById(meowth.getId());
                    Tx.Transaction tx2 = data.transaction(txType);
                    try {
                        pokemonRepository.save(magikarp);
                        magikarpFromDb = pokemonRepository.findById(magikarp.getId());
                        tx2.commit();
                    } catch (RuntimeException ex) {
                        tx2.rollback();
                        throw ex;
                    }
                    tx1.commit();
                } catch (RuntimeException ex) {
                    tx1.rollback();
                    throw ex;
                }
                assertThat(meowthFromDb.isPresent(), is(true));
                assertThat(magikarpFromDb.isPresent(), is(true));
                assertThat(meowthFromDb.get(), is(meowth));
                assertThat(magikarpFromDb.get(), is(magikarp));
            } catch (Error | RuntimeException ex) {
                errors.add(new TestError(entryType, ex));
            } finally {
                pokemonRepository.deleteById(meowth.getId());
                pokemonRepository.deleteById(magikarp.getId());
            }
        }
        errors.forEach(error -> LOGGER.log(Level.ERROR,
                                           String.format("%s: %s failed", testName, error.entryType()),
                                           error.cause()));
        assertThat(errors, is(empty()));
    }

    private record TestError(Tx.Type entryType, Throwable cause) {
    }

}
