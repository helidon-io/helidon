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

import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.app.AbstractQueryService;

/**
 * Service to test simple query statements.
 */
public class SimpleQueryService extends AbstractQueryService {

    /**
     * Create a new instance.
     *
     * @param dbClient   dbclient
     * @param statements statements
     */
    public SimpleQueryService(DbClient dbClient, Map<String, String> statements) {
        super(dbClient, statements);
    }

    @Override
    protected List<DbRow> testCreateNamedQueryStrStrOrderArgs(String name) {
        return dbClient().execute()
                .createNamedQuery("select-pikachu", statement("select-pokemon-order-arg"))
                .addParam(name)
                .execute()
                .toList();
    }

    @Override
    protected List<DbRow> testCreateNamedQueryStrNamedArgs(String name) {
        return dbClient().execute()
                .createNamedQuery("select-pokemon-named-arg")
                .addParam("name", name)
                .execute()
                .toList();
    }

    @Override
    protected List<DbRow> testCreateNamedQueryStrOrderArgs(String name) {
        return dbClient().execute()
                .createNamedQuery("select-pokemon-order-arg")
                .addParam(name)
                .execute()
                .toList();
    }

    @Override
    protected List<DbRow> testCreateQueryNamedArgs(String name) {
        return dbClient().execute()
                .createQuery(statement("select-pokemon-named-arg"))
                .addParam("name", name)
                .execute()
                .toList();
    }

    @Override
    protected List<DbRow> testCreateQueryOrderArgs(String name) {
        return dbClient().execute()
                .createQuery(statement("select-pokemon-order-arg"))
                .addParam(name)
                .execute()
                .toList();
    }

    @Override
    protected List<DbRow> testNamedQueryOrderArgs(String name) {
        return dbClient().execute()
                .namedQuery("select-pokemon-order-arg", name)
                .toList();
    }

    @Override
    protected List<DbRow> testQueryOrderArgs(String name) {
        return dbClient().execute()
                .query(statement("select-pokemon-order-arg"), name)
                .toList();
    }
}
