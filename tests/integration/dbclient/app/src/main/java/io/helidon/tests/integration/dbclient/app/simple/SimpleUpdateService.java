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
import io.helidon.tests.integration.dbclient.app.AbstractUpdateService;

/**
 * Service to test simple update statements.
 */
public class SimpleUpdateService extends AbstractUpdateService {

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public SimpleUpdateService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedUpdateStrStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedUpdate("update-spearow", statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedUpdateStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedUpdate("update-pokemon-named-arg")
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedUpdateStrOrderArgs(int id, String name) {
        return dbClient().execute()
                .createNamedUpdate("update-pokemon-order-arg")
                .addParam(name)
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateUpdateNamedArgs(int id, String name) {
        return dbClient().execute()
                .createUpdate(statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateUpdateOrderArgs(int id, String name) {
        return dbClient().execute()
                .createUpdate(statement("update-pokemon-order-arg"))
                .addParam(name)
                .addParam(id)
                .execute();
    }

    @Override
    protected long testNamedUpdateNamedArgs(int id, String name) {
        return dbClient().execute()
                .namedUpdate("update-pokemon-order-arg", name, id);
    }

    @Override
    protected long testUpdateOrderArgs(int id, String name) {
        return dbClient().execute()
                .update(statement("update-pokemon-order-arg"), name, id);
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-piplup", statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrNamedArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-named-arg")
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithUpdateStrOrderArgs(int id, String name) {
        return dbClient().execute()
                .createNamedDmlStatement("update-pokemon-order-arg")
                .addParam(name)
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateDmlWithUpdateNamedArgs(int id, String name) {
        return dbClient().execute()
                .createDmlStatement(statement("update-pokemon-named-arg"))
                .addParam("name", name)
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateDmlWithUpdateOrderArgs(int id, String name) {
        return dbClient().execute()
                .createDmlStatement(statement("update-pokemon-order-arg"))
                .addParam(name)
                .addParam(id)
                .execute();
    }

    @Override
    protected long testNamedDmlWithUpdateOrderArgs(int id, String name) {
        return dbClient().execute()
                .namedDml("update-pokemon-order-arg", name, id);
    }

    @Override
    protected long testDmlWithUpdateOrderArgs(int id, String name) {
        return dbClient().execute()
                .dml(statement("update-pokemon-order-arg"), name, id);
    }
}
