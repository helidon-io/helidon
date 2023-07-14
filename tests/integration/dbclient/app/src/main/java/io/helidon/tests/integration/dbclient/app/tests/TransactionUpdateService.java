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

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbTransaction;

/**
 * Service to test simple update statements in transaction.
 */
public class TransactionUpdateService extends AbstractUpdateService {

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public TransactionUpdateService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedUpdateStrStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedUpdate("update-spearow", statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedUpdateStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedUpdateStrOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedUpdate("update-pokemon-order-arg")
                .addParam(name)
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateUpdateNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createUpdate(statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateUpdateOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createUpdate(statement("update-pokemon-order-arg"))
                .addParam(name)
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedUpdateNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedUpdate("update-pokemon-order-arg", name, id);
        tx.commit();
        return count;
    }

    @Override
    protected long testUpdateOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.update(statement("update-pokemon-order-arg"), name, id);
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("update-piplup", statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(name)
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithUpdateNamedArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDmlStatement(statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithUpdateOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDmlStatement(statement("update-pokemon-order-arg"))
                .addParam(name)
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedDmlWithUpdateOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedDml("update-pokemon-order-arg", name, id);
        tx.commit();
        return count;
    }

    @Override
    protected long testDmlWithUpdateOrderArgs(int id, String name) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.dml(statement("update-pokemon-order-arg"), name, id);
        tx.commit();
        return count;
    }
}
