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
package io.helidon.tests.integration.dbclient.common.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.utils.RangePoJo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.tests.integration.dbclient.common.utils.VerifyData.verifyPokemonsIdRange;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test DbStatementQuery methods.
 */
@ExtendWith(DbClientParameterResolver.class)
public class QueryStatementIT {

    private final DbClient dbClient;

    QueryStatementIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testQueryArrayParams() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(1, 7)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testQueryListParams() {
        List<Integer> params = new ArrayList<>(2);
        params.add(1);
        params.add(7);
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(params)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testQueryMapParams() {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("idmin", 1);
        params.put("idmax", 7);
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .params(params)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    @Test
    public void testQueryMapMissingParams() {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("id", 1);
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idname-named-arg")
                .params(params)
                .execute();
        assertThat(rows, notNullValue());
        List<DbRow> rowsList = rows.toList();
        assertThat(rowsList, hasSize(0));
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testQueryOrderParam() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .addParam(1)
                .addParam(7)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testQueryNamedParam() {
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .addParam("idmin", 1)
                .addParam("idmax", 7)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testQueryMappedNamedParam() {
        RangePoJo range = new RangePoJo(1, 7);
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testQueryMappedOrderParam() {
        RangePoJo range = new RangePoJo(1, 7);
        Stream<DbRow> rows = dbClient.execute()
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute();

        verifyPokemonsIdRange(rows, 1, 7);
    }

}
