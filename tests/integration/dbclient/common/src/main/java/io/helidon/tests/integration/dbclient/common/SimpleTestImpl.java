/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbResultDml;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;
import io.helidon.tests.integration.dbclient.common.model.Types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Actual implementation of {@link SimpleTests}.
 */
public final class SimpleTestImpl extends AbstractTestImpl implements SimpleTest {

    /**
     * Create a new instance.
     *
     * @param db     db client
     * @param config config
     */
    public SimpleTestImpl(DbClient db, Config config) {
        super(db, config);
    }

    @Override
    public void testCreateNamedDeleteStrStrOrderArgs() {
        Pokemon orig = Pokemons.RAYQUAZA;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute()
                .createNamedDelete("delete-rayquaza", stmt)
                .addParam(orig.id()).execute();
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testCreateNamedDeleteStrNamedArgs() {
        Pokemon orig = Pokemons.LUGIA;
        long result = db.execute()
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", orig.id()).execute();
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testCreateNamedDeleteStrOrderArgs() {
        Pokemon orig = Pokemons.HOOH;
        long result = db.execute()
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(orig.id()).execute();
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testCreateDeleteNamedArgs() {
        Pokemon orig = Pokemons.RAIKOU;
        String stmt = statements.get("delete-pokemon-named-arg");
        long result = db.execute()
                .createDelete(stmt)
                .addParam("id", orig.id()).execute();
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testCreateDeleteOrderArgs() {
        Pokemon orig = Pokemons.GIRATINA;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute()
                .createDelete(stmt)
                .addParam(orig.id()).execute();
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testNamedDeleteOrderArgs() {
        Pokemon orig = Pokemons.REGIROCK;
        long result = db.execute()
                .namedDelete("delete-pokemon-order-arg", orig.id());
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testDeleteOrderArgs() {
        Pokemon orig = Pokemons.KYOGRE;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute()
                .delete(stmt, orig.id());
        verifyDeletePokemon(result, orig);
    }

    @Override
    public void testCreateNamedDmlWithInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(200, "Torchic", Types.FIRE);
        String stmt = statements.get("insert-pokemon-named-arg");
        long result = db.execute()
                .createNamedDmlStatement("insert-torchic", stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDmlWithInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(201, "Combusken", Types.FLYING, Types.FIRE);
        long result = db.execute()
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDmlWithInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(202, "Treecko", Types.GRASS);
        long result = db.execute()
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(pokemon.id()).addParam(pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateDmlWithInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(203, "Grovyle", Types.GRASS);
        String stmt = statements.get("insert-pokemon-named-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(204, "Sceptile", Types.GRASS);
        String stmt = statements.get("insert-pokemon-order-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.id()).addParam(pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testNamedDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(205, "Snover", Types.GRASS, Types.ICE);
        long result = db.execute()
                .namedDml("insert-pokemon-order-arg", pokemon.id(), pokemon.name());
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testDmlWithInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(206, "Abomasnow", Types.GRASS, Types.ICE);
        String stmt = statements.get("insert-pokemon-order-arg");
        long result = db.execute()
                .dml(stmt, pokemon.id(), pokemon.name());
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDmlWithUpdateStrStrNamedArgs() {
        Pokemon orig = Pokemons.PIPLUP;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedPiplup", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        long result = db.execute()
                .createNamedDmlStatement("update-piplup", stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedDmlWithUpdateStrNamedArgs() {
        Pokemon orig = Pokemons.PRINPLUP;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedPrinplup", orig.types());
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedDmlWithUpdateStrOrderArgs() {
        Pokemon orig = Pokemons.EMPOLEON;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedEmpoleon", orig.types());
        long result = db.execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateDmlWithUpdateNamedArgs() {
        Pokemon orig = Pokemons.STARYU;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedStaryu", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateDmlWithUpdateOrderArgs() {
        Pokemon orig = Pokemons.STARMIE;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedStarmie", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testNamedDmlWithUpdateOrderArgs() {
        Pokemon orig = Pokemons.HORSEA;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedHorsea", orig.types());
        long result = db.execute()
                .namedDml("update-pokemon-order-arg", updated.name(), updated.id());
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testDmlWithUpdateOrderArgs() {
        Pokemon orig = Pokemons.SEADRA;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedSeadra", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        long result = db.execute()
                .dml(stmt, updated.name(), updated.id());
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedDmlWithDeleteStrStrOrderArgs() {
        Pokemon pokemon = Pokemons.MUDKIP;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute()
                .createNamedDmlStatement("delete-mudkip", stmt)
                .addParam(pokemon.id())
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDmlWithDeleteStrNamedArgs() {
        Pokemon pokemon = Pokemons.MARSHTOMP;
        long result = db.execute()
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", pokemon.id())
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedDmlWithDeleteStrOrderArgs() {
        Pokemon pokemon = Pokemons.SWAMPERT;
        long result = db.execute()
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(pokemon.id())
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateDmlWithDeleteNamedArgs() {
        Pokemon pokemon = Pokemons.MUK;
        String stmt = statements.get("delete-pokemon-named-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam("id", pokemon.id())
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateDmlWithDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.GRIMER;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute()
                .createDmlStatement(stmt)
                .addParam(pokemon.id())
                .execute();
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testNamedDmlWithDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.CUBCHOO;
        long result = db.execute().namedDml("delete-pokemon-order-arg", pokemon.id());
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testDmlWithDeleteOrderArgs() {
        Pokemon pokemon = Pokemons.BEARTIC;
        String stmt = statements.get("delete-pokemon-order-arg");
        long result = db.execute().dml(stmt, pokemon.id());
        verifyDeletePokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedGetStrStrNamedArgs() {
        Pokemon expected = Pokemons.PIKACHU;
        String stmt = statements.get("select-pokemon-named-arg");
        DbRow row = db.execute()
                .createNamedGet("select-pikachu", stmt)
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedGetStrNamedArgs() {
        Pokemon expected = Pokemons.RAICHU;
        DbRow row = db.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedGetStrOrderArgs() {
        Pokemon expected = Pokemons.MACHOP;
        DbRow row = db.execute()
                .createNamedGet("select-pokemon-order-arg")
                .addParam(expected.name())
                .execute()
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateGetNamedArgs() {
        Pokemon expected = Pokemons.SNORLAX;
        String stmt = statements.get("select-pokemon-named-arg");
        DbRow row = db.execute()
                .createGet(stmt)
                .addParam("name", expected.name())
                .execute()
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateGetOrderArgs() {
        Pokemon expected = Pokemons.CHARIZARD;
        String stmt = statements.get("select-pokemon-order-arg");
        DbRow row = db.execute()
                .createGet(stmt)
                .addParam(expected.name())
                .execute()
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testNamedGetStrOrderArgs() {
        Pokemon expected = Pokemons.MEOWTH;
        DbRow row = db.execute()
                .namedGet("select-pokemon-order-arg", expected.name())
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testGetStrOrderArgs() {
        Pokemon expected = Pokemons.GYARADOS;
        String stmt = statements.get("select-pokemon-order-arg");
        DbRow row = db.execute()
                .get(stmt, expected.name())
                .orElse(null);
        verifyPokemon(row, expected);
    }

    @Override
    public void testCreateNamedInsertStrStrNamedArgs() {
        Pokemon pokemon = new Pokemon(300, "Bulbasaur", Types.POISON, Types.GRASS);
        String stmt = statements.get("insert-pokemon-named-arg");
        long result = db.execute()
                .createNamedInsert("insert-bulbasaur", stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedInsertStrNamedArgs() {
        Pokemon pokemon = new Pokemon(301, "Ivysaur", Types.POISON, Types.GRASS);
        long result = db.execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateNamedInsertStrOrderArgs() {
        Pokemon pokemon = new Pokemon(302, "Venusaur", Types.POISON, Types.GRASS);
        long result = db.execute()
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(pokemon.id()).addParam(pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateInsertNamedArgs() {
        Pokemon pokemon = new Pokemon(303, "Magby", Types.FIRE);
        String stmt = statements.get("insert-pokemon-named-arg");
        long result = db.execute()
                .createInsert(stmt)
                .addParam("id", pokemon.id()).addParam("name", pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testCreateInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(304, "Magmar", Types.FIRE);
        String stmt = statements.get("insert-pokemon-order-arg");
        long result = db.execute()
                .createInsert(stmt)
                .addParam(pokemon.id()).addParam(pokemon.name())
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testNamedInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(305, "Rattata", Types.NORMAL);
        long result = db.execute().namedInsert("insert-pokemon-order-arg", pokemon.id(), pokemon.name());
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testInsertOrderArgs() {
        Pokemon pokemon = new Pokemon(306, "Raticate", Types.NORMAL);
        String stmt = statements.get("insert-pokemon-order-arg");
        long result = db.execute().insert(stmt, pokemon.id(), pokemon.name());
        verifyInsertPokemon(result, pokemon);
    }

    /**
     * Verify {@code namedInsert(String)} API method with named parameters and returned generated keys.
     */
    @Override
    public void testInsertNamedArgsReturnedKeys() throws Exception {
        try (DbResultDml result = db.execute().createNamedInsert("insert-match")
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
        }
    }

    /**
     * Verify {@code namedInsert(String)} API method with named parameters and returned insert columns.
     */
    @Override
    public void testInsertNamedArgsReturnedColumns() throws Exception {
        // Not supported in Mongo, skip the test
        if (db.dbType().equals("mongoDb")) {
            return;
        }
        try (DbResultDml result = db.execute().createNamedInsert("insert-match")
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
        }
    }

    @Override
    public void testCreateNamedQueryStrStrOrderArgs() {
        Pokemon expected = Pokemons.PIKACHU;
        String stmt = statements.get("select-pokemon-order-arg");
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pikachu", stmt)
                .addParam(expected.name())
                .execute();

        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedQueryStrNamedArgs() {
        Pokemon expected = Pokemons.RAICHU;
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", expected.name())
                .execute();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedQueryStrOrderArgs() {
        Pokemon expected = Pokemons.MACHOP;
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(expected.name())
                .execute();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateQueryNamedArgs() {
        Pokemon expected = Pokemons.SNORLAX;
        String stmt = statements.get("select-pokemon-named-arg");
        Stream<DbRow> rows = db.execute()
                .createQuery(stmt)
                .addParam("name", expected.name())
                .execute();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateQueryOrderArgs() {
        Pokemon expected = Pokemons.CHARIZARD;
        String stmt = statements.get("select-pokemon-order-arg");
        Stream<DbRow> rows = db.execute()
                .createQuery(stmt)
                .addParam(expected.name())
                .execute();
        verifyPokemon(rows, expected);
    }

    @Override
    public void testNamedQueryOrderArgs() {
        Pokemon expected = Pokemons.MEOWTH;
        Stream<DbRow> rows = db.execute()
                .namedQuery("select-pokemon-order-arg", expected.name());
        verifyPokemon(rows, expected);
    }

    @Override
    public void testQueryOrderArgs() {
        Pokemon expected = Pokemons.GYARADOS;
        String stmt = statements.get("select-pokemon-order-arg");
        Stream<DbRow> rows = db.execute()
                .query(stmt, expected.name());
        verifyPokemon(rows, expected);
    }

    @Override
    public void testCreateNamedUpdateStrStrNamedArgs() {
        Pokemon orig = Pokemons.SPEAROW;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedSpearow", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        long result = db.execute()
                .createNamedUpdate("update-spearow", stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedUpdateStrNamedArgs() {
        Pokemon orig = Pokemons.FEAROW;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedFearow", orig.types());
        long result = db.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateNamedUpdateStrOrderArgs() {
        Pokemon orig = Pokemons.EKANS;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedEkans", orig.types());
        long result = db.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateUpdateNamedArgs() {
        Pokemon orig = Pokemons.ARBOK;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedArbok", orig.types());
        String stmt = statements.get("update-pokemon-named-arg");
        long result = db.execute()
                .createUpdate(stmt)
                .addParam("name", updated.name()).addParam("id", updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testCreateUpdateOrderArgs() {
        Pokemon orig = Pokemons.SANDSHREW;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedSandshrew", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        long result = db.execute()
                .createUpdate(stmt)
                .addParam(updated.name()).addParam(updated.id())
                .execute();
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testNamedUpdateNamedArgs() {
        Pokemon orig = Pokemons.SANDSLASH;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedSandslash", orig.types());
        long result = db.execute()
                .namedUpdate("update-pokemon-order-arg", updated.name(), updated.id());
        verifyUpdatePokemon(result, updated);
    }

    @Override
    public void testUpdateOrderArgs() {
        Pokemon orig = Pokemons.DIGLETT;
        Pokemon updated = new Pokemon(orig.id(), "UpdatedDiglett", orig.types());
        String stmt = statements.get("update-pokemon-order-arg");
        long result = db.execute()
                .update(stmt, updated.name(), updated.id());
        verifyUpdatePokemon(result, updated);
    }
}
