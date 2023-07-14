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

/**
 * Service to test delete statements.
 */
public class SimpleDeleteService extends AbstractDeleteService {

    /**
     * Create a new instance
     *
     * @param dbClient   DbClient instance
     * @param statements statements from configuration file
     */
    public SimpleDeleteService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected long testCreateNamedDeleteStrStrOrderArgs(int id) {
        return dbClient().execute()
                .createNamedDelete("delete-rayquaza", statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateNamedDeleteStrNamedArgs(int id) {
        return dbClient().execute()
                .createNamedDelete("delete-pokemon-named-arg")
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedDeleteStrOrderArgs(int id) {
        return dbClient().execute()
                .createNamedDelete("delete-pokemon-order-arg")
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateDeleteNamedArgs(int id) {
        return dbClient().execute()
                .createDelete(statement("delete-pokemon-named-arg"))
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateDeleteOrderArgs(int id) {
        return dbClient().execute()
                .createDelete(statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
    }

    @Override
    protected long testNamedDeleteOrderArgs(int id) {
        return dbClient().execute()
                .namedDelete("delete-pokemon-order-arg", id);
    }

    @Override
    protected long testDeleteOrderArgs(int id) {
        return dbClient().execute()
                .delete(statement("delete-pokemon-order-arg"), id);
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrStrOrderArgs(int id) {
        return dbClient().execute()
                .createNamedDmlStatement("delete-mudkip", statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrNamedArgs(int id) {
        return dbClient().execute()
                .createNamedDmlStatement("delete-pokemon-named-arg")
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateNamedDmlWithDeleteStrOrderArgs(int id) {
        return dbClient().execute()
                .createNamedDmlStatement("delete-pokemon-order-arg")
                .addParam(id)
                .execute();
    }

    @Override
    protected long testCreateDmlWithDeleteNamedArgs(int id) {
        return dbClient().execute()
                .createDmlStatement(statement("delete-pokemon-named-arg"))
                .addParam("id", id)
                .execute();
    }

    @Override
    protected long testCreateDmlWithDeleteOrderArgs(int id) {
        return dbClient().execute()
                .createDmlStatement(statement("delete-pokemon-order-arg"))
                .addParam(id)
                .execute();
    }

    @Override
    protected long testNamedDmlWithDeleteOrderArgs(int id) {
        return dbClient().execute()
                .namedDml("delete-pokemon-order-arg", id);
    }

    @Override
    protected long testDmlWithDeleteOrderArgs(int id) {
        return dbClient().execute()
                .dml(statement("delete-pokemon-order-arg"), id);
    }
}
