/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;
import io.helidon.tests.integration.dbclient.common.model.Range;
import io.helidon.tests.integration.dbclient.common.model.Types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Actual implementation of {@link StatementTests}.
 */
public final class StatementTestImpl extends AbstractTestImpl implements StatementTest {

    /**
     * Create a new instance.
     *
     * @param db     db client
     * @param config config
     */
    public StatementTestImpl(DbClient db, Config config) {
        super(db, config);
    }

    @Override
    public void testCreateNamedQueryNonExistentStmt() {
        try {
            List<DbRow> ignored = db.execute()
                    .createNamedQuery("select-pokemons-not-exists")
                    .execute()
                    .toList();
            throw new AssertionError("Execution of non existing statement shall cause an exception to be thrown.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithoutArgs() {
        try {
            List<DbRow> ignored = db.execute()
                    .createNamedQuery("select-pokemons-error-arg")
                    .execute()
                    .toList();
            throw new AssertionError("Execution of query with both named and ordered parameters without passing any shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedAndOrderArgsWithArgs() {
        try {
            Pokemon pokemon = Pokemons.CHARIZARD;
            List<DbRow> ignored = db.execute()
                    .createNamedQuery("select-pokemons-error-arg")
                    .addParam("id", pokemon.id())
                    .addParam(pokemon.name())
                    .execute()
                    .toList();
            throw new AssertionError("Execution of query with both named and ordered parameters without passing them shall fail"
                                     + ".");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryNamedArgsSetOrderArg() {
        try {
            Pokemon pokemon = Pokemons.CHARIZARD;
            List<DbRow> ignored = db.execute()
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam(pokemon.name())
                    .execute()
                    .toList();
            throw new AssertionError("Execution of query with named parameter with passing ordered parameter value shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testCreateNamedQueryOrderArgsSetNamedArg() {
        try {
            Pokemon pokemon = Pokemons.MEOWTH;
            List<DbRow> ignored = db.execute()
                    .createNamedQuery("select-pokemon-order-arg")
                    .addParam("name", pokemon.name())
                    .execute()
                    .toList();
            throw new AssertionError("Execution of query with ordered parameter with passing named parameter value shall fail.");
        } catch (Throwable ex) {
            // expected
        }
    }

    @Override
    public void testGetArrayParams() {
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(1, 3)
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 1, 3);
    }

    @Override
    public void testGetListParams() {
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(List.of(2, 4))
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 2, 4);
    }

    @Override
    public void testGetMapParams() {
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .params(Map.of(
                        "idmin", 3,
                        "idmax", 5))
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 3, 5);
    }

    @Override
    public void testGetOrderParam() {
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .addParam(4)
                .addParam(6)
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 4, 6);
    }

    @Override
    public void testGetNamedParam() {
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .addParam("idmin", 5)
                .addParam("idmax", 7)
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 5, 7);
    }

    @Override
    public void testGetMappedNamedParam() {
        Range range = new Range(0, 2);
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 0, 2);
    }

    @Override
    public void testGetMappedOrderParam() {
        Range range = new Range(6, 8);
        DbRow row = db.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute()
                .orElse(null);
        verifyPokemonsIdRange(row, 6, 8);
    }

    @Override
    public void testQueryArrayParams() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(1, 7)
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryListParams() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(List.of(1, 7))
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryMapParams() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .params(Map.of(
                        "idmin", 1,
                        "idmax", 7))
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryMapMissingParams() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idname-named-arg")
                .params(Map.of("id", 1))
                .execute();
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, hasSize(0));
    }

    @Override
    public void testQueryOrderParam() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .addParam(1)
                .addParam(7)
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryNamedParam() {
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .addParam("idmin", 1)
                .addParam("idmax", 7)
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryMappedNamedParam() {
        Range range = new Range(1, 7);
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testQueryMappedOrderParam() {
        Range range = new Range(1, 7);
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute();

        verifyPokemonsIdRange(rows);
    }

    @Override
    public void testDmlArrayParams() {
        Pokemon orig = Pokemons.SHINX;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedShinx", Types.ELECTRIC);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(updated.name(), updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlListParams() {
        Pokemon orig = Pokemons.LUXIO;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedLuxio", Types.ELECTRIC);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .params(List.of(updated.name(), updated.id()))
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlMapParams() {
        Pokemon orig = Pokemons.LUXRAY;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedLuxray", Types.ELECTRIC);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .params(Map.of(
                        "name", updated.name(),
                        "id", updated.id()))
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlOrderParam() {
        Pokemon orig = Pokemons.KRICKETOT;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedKricketot", Types.BUG);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updated.name())
                .addParam(updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlNamedParam() {
        Pokemon orig = Pokemons.KRICKETUNE;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedKricketune", Types.BUG);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updated.name())
                .addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlMappedNamedParam() {
        Pokemon orig = Pokemons.PHIONE;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedPhione", Types.NORMAL, Types.FLYING);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .namedParam(updated)
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlMappedOrderParam() {
        Pokemon orig = Pokemons.CHATOT;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedChatot", Types.WATER);
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .indexedParam(updated)
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    private void verifyPokemonsIdRange(Stream<DbRow> rows) {
        Map<Integer, Pokemon> valid = range(1, 7);

        // Compare result with valid data
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, hasSize(valid.size()));
        for (DbRow row : rowsList) {
            int id = row.column(1).get(Integer.class);
            String name = row.column(2).get(String.class);
            assertThat(valid.containsKey(id), equalTo(true));
            assertThat(name, equalTo(valid.get(id).name()));
        }
    }
}
