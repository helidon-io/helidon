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
package io.helidon.tests.integration.dbclient.app.simple;

import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.app.AbstractInsertService;

/**
 * Service to test simple insert statements.
 */
public class SimpleInsertService extends AbstractInsertService {

    /**
     * Creates an instance of web resource to test set of basic DbClient inserts.
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleInsertService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedInsertStrStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedInsert("insert-bulbasaur", statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateNamedInsertStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedInsert("insert-pokemon-named-arg")
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateNamedInsertStrOrderArgs(int id, String name) {
        return dbClient().execute()
                .createNamedInsert("insert-pokemon-order-arg")
                .addParam(id)
                .addParam(name)
                .execute();
    }

    @Override
    protected long testCreateInsertNamedArgs(int id, String name) {
        return dbClient().execute()
                .createInsert(statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .createInsert(statement("insert-pokemon-order-arg"))
                .addParam(id)
                .addParam(name)
                .execute();
    }

    @Override
    protected long testNamedInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .namedInsert("insert-pokemon-order-arg", id, name);
    }

    @Override
    protected long testInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .insert(statement("insert-pokemon-order-arg"), id, name);
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("insert-torchic", statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("insert-pokemon-named-arg")
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithInsertStrOrderArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("insert-pokemon-order-arg")
                .addParam(id)
                .addParam(name)
                .execute();
    }

    @Override
    protected long testCreateDmlWithInsertNamedArgs(int id, String name) {
        return dbClient().execute()
                .createDmlStatement(statement("insert-pokemon-named-arg"))
                .addParam("id", id)
                .addParam("name", name)
                .execute();
    }

    @Override
    protected long testCreateDmlWithInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .createDmlStatement(statement("insert-pokemon-order-arg"))
                .addParam(id)
                .addParam(name)
                .execute();
    }

    @Override
    protected long testNamedDmlWithInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .namedDml("insert-pokemon-order-arg", id, name);
    }

    @Override
    protected long testDmlWithInsertOrderArgs(int id, String name) {
        return dbClient().execute()
                .dml(statement("insert-pokemon-order-arg"), id, name);
    }
}
