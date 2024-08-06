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
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbClientService;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;
import io.helidon.tests.integration.dbclient.common.model.Type;
import io.helidon.tests.integration.dbclient.common.model.Types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Actual implementation of {@link MiscTest}.
 */
public final class MiscTestImpl extends AbstractTestImpl implements MiscTest {

    /**
     * Create a new instance.
     *
     * @param db     db client
     * @param config config
     */
    public MiscTestImpl(DbClient db, Config config) {
        super(db, config);
    }

    @Override
    public void testFlowControl() {
        List<Type> actual = db.execute().namedQuery("select-types")
                .map(row -> new Type(row.column(1).get(Integer.class), row.column(2).get(String.class)))
                .toList();
        assertThat(actual.size(), equalTo(18));
        assertThat(actual, is(Types.ALL));
    }

    @Override
    public void testStatementInterceptor() {
        TestDbClientService interceptor = new TestDbClientService();
        try (DbClient db = DbClient.builder(config.get("db")).addService(interceptor).build()) {
            db.execute()
                    .createNamedQuery("select-pokemon-named-arg")
                    .addParam("name", Pokemons.MEOWTH.name())
                    .execute();
            assertThat(interceptor.called(), equalTo(true));
        }
    }

    @Override
    public void testInsertWithOrderMapping() {
        Pokemon pokemon = new Pokemon(600, "Articuno", Types.FLYING, Types.ICE);
        long result = db.execute()
                .createNamedInsert("insert-pokemon-order-arg-rev")
                .indexedParam(pokemon)
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testInsertWithNamedMapping() {
        Pokemon pokemon = new Pokemon(601, "Zapdos", Types.FLYING, Types.ELECTRIC);
        long result = db.execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyInsertPokemon(result, pokemon);
    }

    @Override
    public void testUpdateWithOrderMapping() {
        Pokemon pokemon = new Pokemon(100, "UpdatedMasquerain", Types.FLYING, Types.ICE);
        long result = db.execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .indexedParam(pokemon)
                .execute();
        verifyUpdatePokemon(result, pokemon);
    }

    @Override
    public void testUpdateWithNamedMapping() {
        Pokemon pokemon = new Pokemon(99, "UpdatedMoltres", Types.FLYING, Types.ELECTRIC);
        long result = db.execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .namedParam(pokemon)
                .execute();
        verifyUpdatePokemon(result, pokemon);
    }

    @Override
    public void testDeleteWithOrderMapping() {
        Pokemon pokemon = Pokemons.MAKUHITA;
        long result = db.execute()
                .createNamedDelete("delete-pokemon-full-order-arg")
                .indexedParam(pokemon)
                .execute();

        assertThat(result, equalTo(1L));
        DbRow row = db.execute()
                .namedGet("select-pokemon-by-id", pokemon.id())
                .orElse(null);
        assertThat(row, is(nullValue()));
    }

    @Override
    public void testDeleteWithNamedMapping() {
        Pokemon pokemon = Pokemons.HARIYAMA;

        long result = db.execute()
                .createNamedDelete("delete-pokemon-full-named-arg")
                .namedParam(pokemon)
                .execute();

        assertThat(result, equalTo(1L));
        DbRow row = db.execute()
                .namedGet("select-pokemon-by-id", pokemon.id())
                .orElse(null);
        assertThat(row, is(nullValue()));
    }

    @Override
    public void testQueryWithMapping() {
        Pokemon orig = Pokemons.RAICHU;
        Stream<DbRow> rows = db.execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", orig.name())
                .execute();

        Pokemon actual = rows.map(it -> it.as(Pokemon.class)).findFirst().orElseThrow();
        verifyPokemon(actual, orig);
    }

    @Override
    public void testGetWithMapping() {
        Pokemon orig = Pokemons.MACHOP;
        DbRow row = db.execute()
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", orig.name())
                .execute()
                .orElse(null);

        assertThat(row, is(not(nullValue())));
        Pokemon actual = row.as(Pokemon.class);
        verifyPokemon(actual, orig);
    }

    private static final class TestDbClientService implements DbClientService {

        private boolean called;

        private TestDbClientService() {
            this.called = false;
        }

        @Override
        public DbClientServiceContext statement(DbClientServiceContext context) {
            this.called = true;
            return context;
        }

        private boolean called() {
            return called;
        }

    }
}
