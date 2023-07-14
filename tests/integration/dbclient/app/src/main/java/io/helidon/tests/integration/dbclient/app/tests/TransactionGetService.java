/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.app.tests;

import java.util.Map;
import java.util.Optional;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbTransaction;

/**
 * Service to test set of get statements calls in transaction.
 */
public class TransactionGetService extends AbstractGetService {

    public TransactionGetService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected Optional<DbRow> testCreateNamedGetStrStrNamedArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx
                .createNamedGet("select-pikachu", statement("select-pokemon-named-arg"))
                .addParam("name", name)
                .execute();
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testCreateNamedGetStrNamedArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx
                .createNamedGet("select-pokemon-named-arg")
                .addParam("name", name)
                .execute();
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testCreateNamedGetStrOrderArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx
                .createNamedGet("select-pokemon-order-arg")
                .addParam(name)
                .execute();
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testCreateGetNamedArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx
                .createGet(statement("select-pokemon-named-arg"))
                .addParam("name", name)
                .execute();
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testCreateGetOrderArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx
                .createGet(statement("select-pokemon-order-arg"))
                .addParam(name)
                .execute();
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testNamedGetStrOrderArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx.namedGet("select-pokemon-order-arg", name);
        tx.commit();
        return result;
    }

    @Override
    protected Optional<DbRow> testGetStrOrderArgs(String name) {
        DbTransaction tx = dbClient().transaction();
        Optional<DbRow> result = tx.get(statement("select-pokemon-order-arg"), name);
        tx.commit();
        return result;
    }
}
