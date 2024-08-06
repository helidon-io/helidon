/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbResultDml;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;
import io.helidon.tests.integration.dbclient.common.model.Types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Actual implementation of {@link TransactionTests}.
 */
public final class TransactionTestImpl extends AbstractTestImpl implements TransactionTest {

    /**
     * Create a new instance.
     *
     * @param db     db client
     * @param config config
     */
    public TransactionTestImpl(DbClient db, Config config) {
        super(db, config);
    }

    @Override
    public void testCreateNamedDeleteStrStrOrderArgs() {
        Pokemon pokemon = Pokemons.OMANYTE;
        String stmt = statements.get("delete-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedDelete("delete-rayquaza", stmt)
                .addParam(pokemon.id())
                .execute();
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDeleteStrNamedArgs() {
        Pokemon pokemon = Pokemons.OMASTAR;
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", pokemon.id())
                .execute();
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDeleteStrOrderArgs() {
        Pokemon pokemon = Pokemons.KABUTO;
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(pokemon.id())
                .execute();
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateDeleteNamedArgs() {
        Pokemon pokemon = Pokemons.KABUTOPS;
        String stmt = statements.get("delete-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createDelete(stmt)
                .addParam("id", pokemon.id())
                .execute();
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.CHIKORITA;
        String stmt = statements.get("delete-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createDelete(stmt)
                .addParam(pokemon.id())
                .execute();
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testNamedDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.BAYLEEF;
        DbTransaction tx = db.transaction();
        long result = tx
                .namedDelete("delete-pokemon-order-arg", pokemon.id());
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.MEGANIUM;
        String stmt = statements.get("delete-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .delete(stmt, pokemon.id());
        tx.commit();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedQueryNonExistentStmt() {
        try {
            DbTransaction tx = db.transaction();
            List<DbRow> ignored = tx.createNamedQuery("select-pokemons-not-exists")
                    .execute()
                    .toList();
            tx.commit();
            throw new AssertionError("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        try {
            DbTransaction tx = db.transaction();
            List<DbRow> ignored = tx.createNamedQuery("select-pokemons-error-arg")
                    .execute()
                    .toList();
            tx.commit();
            throw new AssertionError("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        try {
            DbTransaction tx = db.transaction();
            List<DbRow> ignored = tx.createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", 6789)
                    .addParam("a-name")
                    .execute()
                    .toList();
            tx.commit();
            throw new AssertionError("Execution of query with both named and ordered parameters without passing them shall fail"
                                     + ".");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        try {
            DbTransaction tx = db.transaction();
            List<DbRow> ignored = tx.createNamedQuery("select-pokemon-named-arg")
                    .addParam("a-name")
                    .execute()
                    .toList();
            tx.commit();
            throw new AssertionError("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        try {
            DbTransaction tx = db.transaction();
            List<DbRow> ignored = tx.createNamedQuery("select-pokemon-order-arg")
                    .addParam("name", "a-name")
                    .execute()
                    .toList();
            tx.commit();
            throw new AssertionError("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedGetStrStrNamedArgs() {
        Pokemon expected = Pokemons.PIKACHU;
        String stmt = statements.get("select-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .createNamedGet("select-pikachu", stmt)
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        tx.commit();
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedGetStrNamedArgs() {
        Pokemon expected = Pokemons.RAICHU;
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedGetStrOrderArgs() {
        Pokemon expected = Pokemons.MACHOP;
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .createNamedGet("select-pokemon-order-arg")
                .addParam(expected.name())
                .execute()
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateGetNamedArgs() {
        Pokemon expected = Pokemons.SNORLAX;
        String stmt = statements.get("select-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .createGet(stmt)
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateGetOrderArgs() {
        Pokemon expected = Pokemons.CHARIZARD;
        String stmt = statements.get("select-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .createGet(stmt)
                .addParam(expected.name())
                .execute()
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testNamedGetStrOrderArgs() {
        Pokemon expected = Pokemons.MEOWTH;
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .namedGet("select-pokemon-order-arg", expected.name())
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testGetStrOrderArgs() {
        Pokemon expected = Pokemons.GYARADOS;
        String stmt = statements.get("select-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        DbRow row = tx
                .get(stmt, expected.name())
                .orElse(null);
        tx.commit();

        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(85, "Sentret", Types.NORMAL);
        String stmt = statements.get("insert-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedInsert("insert-bulbasaur", stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name()).execute();
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(86, "Furret", Types.NORMAL);
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", pokemon.id()).addParam("name", pokemon.name()).execute();
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(87, "Chinchou", Types.WATER, Types.ELECTRIC);
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(pokemon.id()).addParam(pokemon.name()).execute();
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(88, "Lanturn", Types.WATER, Types.ELECTRIC);
        String stmt = statements.get("insert-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createInsert(stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name()).execute();
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(89, "Swinub", Types.GROUND, Types.ICE);
        String stmt = statements.get("insert-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createInsert(stmt)
                .addParam(pokemon.id()).addParam(pokemon.name()).execute();
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testNamedInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(90, "Piloswine", Types.GROUND, Types.ICE);
        DbTransaction tx = db.transaction();
        long result = tx
                .namedInsert("insert-pokemon-order-arg", pokemon.id(), pokemon.name());
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(91, "Mamoswine", Types.GROUND, Types.ICE);
        String stmt = statements.get("insert-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .insert(stmt, pokemon.id(), pokemon.name());
        tx.commit();
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedInsert(String)} API method with named parameters and returned generated keys.
     */
    @Override
    public void testInsertNamedArgsReturnedKeys() throws Exception {
        DbTransaction tx = db.transaction();
        try (DbResultDml result = tx.createNamedInsert("insert-match")
                .addParam("red", Pokemons.RAICHU.id())
                .addParam("blue", Pokemons.MACHOP.id())
                .returnGeneratedKeys()
                .insert()) {
            List<DbRow> keys = result.generatedKeys().toList();
            long records = result.result();
            assertThat(records, equalTo(1L));
            assertThat(keys, hasSize(1));
            DbRow keysRow = keys.getFirst();
            AtomicInteger columnsCount = new AtomicInteger(0);
            keysRow.forEach(dbColumn -> columnsCount.incrementAndGet());
            DbColumn keyByName;
            // Result is vendor dependent
            switch(db.dbType()) {
                case "mongoDb":
                    assertThat(columnsCount.get(), equalTo(1));
                    keyByName = keysRow.column("_id");
                    break;
                case "jdbc:h2":
                    assertThat(columnsCount.get(), equalTo(1));
                    keyByName = keysRow.column("id");
                    break;
                case "jdbc:mysql":
                    assertThat(columnsCount.get(), equalTo(1));
                    keyByName = keysRow.column("GENERATED_KEY");
                    break;
                case "jdbc:postgresql":
                    assertThat(columnsCount.get(), equalTo(3));
                    keyByName = keysRow.column("id");
                    break;
                case "jdbc:oracle":
                    assertThat(columnsCount.get(), equalTo(1));
                    keyByName = keysRow.column("ROWID");
                    break;
                default:
                    throw new IllegalStateException("Unknown database type: " + db.dbType());
            }
            DbColumn keyByIndex = keysRow.column(1);
            assertThat(keyByName, equalTo(keyByIndex));
            tx.commit();
        } catch (Exception ex) {
            tx.rollback();
        }
    }

    /**
     * Verify {@code namedInsert(String)} API method with named parameters and returned insert columns.
     */
    @Override
    public void testInsertNamedArgsReturnedColumns() throws Exception {
        DbTransaction tx = db.transaction();
        try (DbResultDml result = tx.createNamedInsert("insert-match")
                .addParam("red", Pokemons.SNORLAX.id())
                .addParam("blue", Pokemons.CHARIZARD.id())
                .returnColumns(List.of("id", "red"))
                .insert()) {
            List<DbRow> keys = result.generatedKeys().toList();
            long records = result.result();
            assertThat(records, equalTo(1L));
            assertThat(keys, hasSize(1));
            DbRow keysRow = keys.getFirst();
            AtomicInteger columnsCount = new AtomicInteger(0);
            keysRow.forEach(dbColumn -> columnsCount.incrementAndGet());
            DbColumn idByName;
            DbColumn idByIndex;
            DbColumn redByName;
            DbColumn redByIndex;
            // Result is vendor dependent
            switch(db.dbType()) {
                case "mongoDb":
                    break;
                case "jdbc:h2":
                    assertThat(columnsCount.get(), equalTo(2));
                    idByName = keysRow.column("id");
                    idByIndex = keysRow.column(1);
                    assertThat(idByName, equalTo(idByIndex));
                    redByName = keysRow.column("red");
                    redByIndex = keysRow.column(2);
                    assertThat(redByName, equalTo(redByIndex));
                    break;
                case "jdbc:mysql":
                    assertThat(columnsCount.get(), equalTo(1));
                    idByName = keysRow.column("GENERATED_KEY");
                    idByIndex = keysRow.column(1);
                    assertThat(idByName, equalTo(idByIndex));
                    break;
                case "jdbc:postgresql":
                    assertThat(columnsCount.get(), equalTo(2));
                    idByName = keysRow.column("id");
                    idByIndex = keysRow.column(1);
                    assertThat(idByName, equalTo(idByIndex));
                    redByName = keysRow.column("red");
                    redByIndex = keysRow.column(2);
                    assertThat(redByName, equalTo(redByIndex));
                    break;
                case "jdbc:oracle":
                    assertThat(columnsCount.get(), equalTo(2));
                    idByName = keysRow.column("ID");
                    idByIndex = keysRow.column(1);
                    assertThat(idByName, equalTo(idByIndex));
                    redByName = keysRow.column("RED");
                    redByIndex = keysRow.column(2);
                    assertThat(redByName, equalTo(redByIndex));
                    break;
                default:
                    throw new IllegalStateException("Unknown database type: " + db.dbType());
            }
            tx.commit();
        } catch (Exception ex) {
            tx.rollback();
        }
    }

    @Override
    public void testCreateNamedQueryStrStrOrderArgs() {
        Pokemon expected = Pokemons.PIKACHU;
        String stmt = statements.get("select-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .createNamedQuery("select-pikachu", stmt)
                .addParam(expected.name())
                .execute()
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedQueryStrNamedArgs() {
        Pokemon expected = Pokemons.RAICHU;
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", expected.name())
                .execute()
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedQueryStrOrderArgs() {
        Pokemon expected = Pokemons.MACHOP;
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(expected.name())
                .execute()
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateQueryNamedArgs() {
        Pokemon expected = Pokemons.SNORLAX;
        String stmt = statements.get("select-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .createQuery(stmt)
                .addParam("name", expected.name())
                .execute()
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateQueryOrderArgs() {
        Pokemon expected = Pokemons.CHARIZARD;
        String stmt = statements.get("select-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .createQuery(stmt)
                .addParam(expected.name())
                .execute()
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testNamedQueryOrderArgs() {
        Pokemon expected = Pokemons.MEOWTH;
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .namedQuery("select-pokemon-order-arg", expected.name())
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testQueryOrderArgs() {
        Pokemon expected = Pokemons.GYARADOS;
        String stmt = statements.get("select-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        List<DbRow> rows = tx
                .query(stmt, expected.name())
                .toList();
        tx.commit();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedUpdateStrStrNamedArgs() {
        Pokemon orig = Pokemons.TEDDIURSA;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedTeddiursa", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-named-arg", stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedUpdateStrNamedArgs() {
        Pokemon orig = Pokemons.URSARING;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedUrsaring", orig.types());
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedUpdateStrOrderArgs() {
        Pokemon orig = Pokemons.SLUGMA;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedSlugma", orig.types());
        DbTransaction tx = db.transaction();
        long result = tx
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateUpdateNamedArgs() {
        Pokemon orig = Pokemons.MAGCARGO;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedMagcargo", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateUpdateOrderArgs() {
        Pokemon orig = Pokemons.LOTAD;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedLotad", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .createUpdate(stmt)
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testNamedUpdateNamedArgs() {
        Pokemon orig = Pokemons.LUDICOLO;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedLudicolo", orig.types());
        DbTransaction tx = db.transaction();
        long result = tx
                .namedUpdate("update-pokemon-order-arg", updated.name(), updated.id());
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testUpdateOrderArgs() {
        Pokemon orig = Pokemons.LOMBRE;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedLombre", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        DbTransaction tx = db.transaction();
        long result = tx
                .update(stmt, updated.name(), updated.id());
        tx.commit();
        verifyUpdatePokemon(result, updated);
    }
}
