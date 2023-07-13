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
import io.helidon.tests.integration.dbclient.app.AbstractDeleteService;

/**
 * Service to test delete statements in transaction.
 */
public class TransactionDeleteService extends AbstractDeleteService {

    public TransactionDeleteService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedDeleteStrStrOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDelete("delete-rayquaza", statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDeleteStrNamedArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDeleteStrOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDelete("delete-pokemon-order-arg")
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDeleteNamedArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDelete(statement("delete-pokemon-named-arg"))
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDelete(statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedDelete("delete-pokemon-order-arg", id);
        tx.commit();
        return count;
    }

    @Override
    protected long testDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.delete(statement("delete-pokemon-order-arg"), id);
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrStrOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("delete-mudkip", statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrNamedArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithDeleteNamedArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.createDmlStatement(statement("delete-pokemon-named-arg"))
                .addParam("id", id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testCreateDmlWithDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx
                .createDmlStatement(statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
        tx.commit();
        return count;
    }

    @Override
    protected long testNamedDmlWithDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.namedDml("delete-pokemon-order-arg", id);
        tx.commit();
        return count;
    }

    @Override
    protected long testDmlWithDeleteOrderArgs(int id) {
        DbTransaction tx = dbClient().transaction();
        long count = tx.dml(statement("delete-pokemon-order-arg"), id);
        tx.commit();
        return count;
    }
}
