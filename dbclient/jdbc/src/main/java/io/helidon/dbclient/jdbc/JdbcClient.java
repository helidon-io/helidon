/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.sql.Connection;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.common.CommonClient;
import io.helidon.dbclient.common.CommonClientContext;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcClient extends CommonClient implements DbClient {
    private final JdbcConnectionPool connectionPool;

    JdbcClient(JdbcClientBuilder builder) {
        super(CommonClientContext.create(builder.statements(),
                                         builder.dbMapperManager(),
                                         builder.mapperManager(),
                                         builder.connectionPool().dbType()));
        connectionPool = builder.connectionPool();
    }

    @Override
    public JdbcExecute execute() {
        return JdbcExecute.create(context(), connectionPool);
    }

    @Override
    public JdbcTransaction transaction() {
        return JdbcTransaction.create(context(), connectionPool);
    }

    @Override
    public String dbType() {
        return connectionPool.dbType();
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (Connection.class.isAssignableFrom(cls)) {
            return cls.cast(connectionPool.connection());
        } else {
            throw new UnsupportedOperationException(String.format("Class %s is not supported for unwrap", cls.getName()));
        }
    }

}
