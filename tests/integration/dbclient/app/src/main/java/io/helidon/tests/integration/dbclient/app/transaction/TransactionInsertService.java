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
package io.helidon.tests.integration.dbclient.app.transaction;

import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;
import io.helidon.tests.integration.dbclient.app.AbstractInsertService;

/**
 * Service to test simple insert statements in transaction.
 */
public class TransactionInsertService extends AbstractInsertService {

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public TransactionInsertService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedInsertStrStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx
                .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedInsertStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedInsertStrOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedInsert("insert-pokemon-order-arg")
                .addParam(id)
                .addParam(name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateInsertNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createInsert(statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createInsert(statement("insert-pokemon-order-arg"))
                .addParam(id)
                .addParam(name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedInsert("insert-pokemon-order-arg", id, name);
        tx.commit();
        return count;
    }

    @Override
    protected long testInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.insert(statement("insert-pokemon-order-arg"), id, name);
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(id)
                .addParam(name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithInsertNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDmlStatement(statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDmlStatement(statement("insert-pokemon-order-arg"))
                .addParam(id)
                .addParam(name)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedDmlWithInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedDml("insert-pokemon-order-arg", id, name);
        tx.commit();
        return count;
    }

    @Override
    protected long testDmlWithInsertOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.dml(statement("insert-pokemon-order-arg"), id, name);
        tx.commit();
        return count;
    }
}
