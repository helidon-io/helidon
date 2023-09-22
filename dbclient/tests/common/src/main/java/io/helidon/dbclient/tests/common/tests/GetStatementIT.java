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
package io.helidon.dbclient.tests.common.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.tests.common.utils.RangePoJo;

import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.utils.VerifyData.verifyCrittersIdRange;

/**
 * Test DbStatementGet methods.
 */
public abstract class GetStatementIT {

    private final DbClient dbClient;

    public GetStatementIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     */
    @Test
    public void testGetArrayParams() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(1, 3)
                .execute();
        verifyCrittersIdRange(maybeRow, 1, 3);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     */
    @Test
    public void testGetListParams() {
        List<Integer> params = new ArrayList<>(2);
        params.add(2);
        params.add(4);
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(params)
                .execute();

        verifyCrittersIdRange(maybeRow, 2, 4);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     */
    @Test
    public void testGetMapParams() {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("idmin", 3);
        params.put("idmax", 5);
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .params(params)
                .execute();

        verifyCrittersIdRange(maybeRow, 3, 5);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     */
    @Test
    public void testGetOrderParam() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .addParam(4)
                .addParam(6)
                .execute();
        verifyCrittersIdRange(maybeRow, 4, 6);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     */
    @Test
    public void testGetNamedParam() {
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .addParam("idmin", 5)
                .addParam("idmax", 7)
                .execute();
        verifyCrittersIdRange(maybeRow, 5, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testGetMappedNamedParam() {
        RangePoJo range = new RangePoJo(0, 2);
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute();
        verifyCrittersIdRange(maybeRow, 0, 2);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     */
    @Test
    public void testGetMappedOrderParam() {
        RangePoJo range = new RangePoJo(6, 8);
        Optional<DbRow> maybeRow = dbClient.execute()
                .createNamedGet("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute();
        verifyCrittersIdRange(maybeRow, 6, 8);
    }

}
