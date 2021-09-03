/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.utils.RangePoJo;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemonsIdRange;

/**
 * Test DbStatementGet methods.
 */
public class GetStatementIT {

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testGetArrayParams() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(1, 3)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(maybeRow, 1, 3);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     */
    @Test
    public void testGetListParams() {
        List<Integer> params = new ArrayList<>(2);
        params.add(2);
        params.add(4);
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-order-arg")
                .params(params)
                .execute())
                .await();

        verifyPokemonsIdRange(maybeRow, 2, 4);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     */
    @Test
    public void testGetMapParams() {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("idmin", 3);
        params.put("idmax", 5);
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-named-arg")
                .params(params)
                .execute())
                .await();

        verifyPokemonsIdRange(maybeRow, 3, 5);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     */
    @Test
    public void testGetOrderParam() {
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-order-arg")
                .addParam(4)
                .addParam(6)
                .execute())
                .await();

        verifyPokemonsIdRange(maybeRow, 4, 6);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testGetNamedParam() throws ExecutionException, InterruptedException {
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-named-arg")
                .addParam("idmin", 5)
                .addParam("idmax", 7)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(maybeRow, 5, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testGetMappedNamedParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(0, 2);
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(maybeRow, 0, 2);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testGetMappedOrderParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(6, 8);
        Optional<DbRow> maybeRow = DB_CLIENT.execute(exec -> exec
                .createNamedGet("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(maybeRow, 6, 8);
    }

}
