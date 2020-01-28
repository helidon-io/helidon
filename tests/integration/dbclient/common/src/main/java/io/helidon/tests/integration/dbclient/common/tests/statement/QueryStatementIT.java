/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutionException;

import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRows;
import io.helidon.tests.integration.dbclient.common.AbstractIT;
import io.helidon.tests.integration.dbclient.common.utils.RangePoJo;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.utils.Utils.verifyPokemonsIdRange;

/**
 * Test DbStatementQuery methods.
 */
public class QueryStatementIT extends AbstractIT {

    /**
     * Verify {@code params(Object... parameters)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryArrayParams() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(1, 7)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(List<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryListParams() throws ExecutionException, InterruptedException {
        List<Integer> params = new ArrayList<>(2);
        params.add(1);
        params.add(7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code params(Map<?>)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMapParams() throws ExecutionException, InterruptedException {
        Map<String, Integer> params = new HashMap<>(2);
        params.put("idmin", 1);
        params.put("idmax", 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .params(params)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code addParam(Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryOrderParam() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .addParam(1)
                .addParam(7)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code addParam(String name, Object parameter)} parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryNamedParam() throws ExecutionException, InterruptedException {
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .addParam("idmin", 1)
                .addParam("idmax", 7)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMappedNamedParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(1, 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-named-arg")
                .namedParam(range)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

    /**
     * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
     *
     * @throws ExecutionException when database query failed
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    public void testQueryMappedOrderParam() throws ExecutionException, InterruptedException {
        RangePoJo range = new RangePoJo(1, 7);
        DbRows<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .createNamedQuery("select-pokemons-idrng-order-arg")
                .indexedParam(range)
                .execute()
        ).toCompletableFuture().get();
        verifyPokemonsIdRange(rows, 1, 7);
    }

}
