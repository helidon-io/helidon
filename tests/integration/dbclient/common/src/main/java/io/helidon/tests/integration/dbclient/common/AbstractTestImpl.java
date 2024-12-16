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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.model.Pokemon;
import io.helidon.tests.integration.dbclient.common.model.Pokemons;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Base test implementation.
 */
public abstract class AbstractTestImpl {

    protected final DbClient db;
    protected final Config config;
    protected final Map<String, String> statements;

    protected AbstractTestImpl(DbClient db, Config config) {
        this.db = db;
        this.config = config;
        this.statements = config.get("db.statements").detach().asMap().get();
    }

    static void verifyPokemon(List<DbRow> rows, Pokemon expected) {
        assertThat(rows, notNullValue());
        assertThat(rows, hasSize(1));
        DbRow row = rows.getFirst();
        int id = row.column(1).get(Integer.class);
        String name = row.column(2).get(String.class);
        assertThat(id, equalTo(expected.id()));
        assertThat(name, expected.name().equals(name));
    }

    static void verifyPokemon(Stream<DbRow> rows, Pokemon pokemon) {
        assertThat(rows, notNullValue());
        verifyPokemon(rows.toList(), pokemon);
    }

    static void verifyPokemon(DbRow row, Pokemon expected) {
        assertThat(row, is(not(nullValue())));
        int id = row.column(1).get(Integer.class);
        String name = row.column(2).get(String.class);
        assertThat(id, equalTo(expected.id()));
        assertThat(name, expected.name().equals(name));
    }

    void verifyPokemonsIdRange(DbRow row, int idMin, int idMax) {
        Map<Integer, Pokemon> valid = range(idMin, idMax);
        assertThat(row, is(not(nullValue())));
        int id = row.column(1).get(Integer.class);
        String name = row.column(2).get(String.class);
        assertThat(valid.containsKey(id), equalTo(true));
        assertThat(name, equalTo(valid.get(id).name()));
    }

    static void verifyPokemon(Pokemon actual, Pokemon expected) {
        assertThat(actual.id(), equalTo(expected.id()));
        assertThat(actual.name(), equalTo(expected.name()));
    }

    void verifyInsertPokemon(long result, Pokemon data) {
        assertThat(result, equalTo(1L));
        DbRow row = db.execute()
                .namedGet("select-pokemon-by-id", data.id())
                .orElse(null);

        assertThat(row, is(not(nullValue())));
        int id = row.column("id").get(Integer.class);
        String name = row.column("name").get(String.class);
        assertThat(id, equalTo(data.id()));
        assertThat(name, data.name().equals(name));
    }

    void verifyUpdatePokemon(long result, Pokemon data) {
        assertThat(result, equalTo(1L));
        DbRow row = db.execute()
                .namedGet("select-pokemon-by-id", data.id())
                .orElse(null);
        verifyPokemon(row, data);
    }

    void verifyDeletePokemon(long result, Pokemon expected) {
        assertThat(result, equalTo(1L));
        DbRow row = db.execute()
                .namedGet("select-pokemon-by-id", expected.id())
                .orElse(null);
        assertThat(row, is(nullValue()));
    }

    Map<Integer, Pokemon> range(int idMin, int idMax) {
        return Pokemons.ALL.stream()
                .filter(p -> p.id() > idMin && p.id() < idMax)
                .collect(Collectors.toMap(Pokemon::id, Function.identity()));
    }
}
