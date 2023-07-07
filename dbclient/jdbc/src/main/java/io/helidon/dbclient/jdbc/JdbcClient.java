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
import io.helidon.dbclient.DbClientBase;
import io.helidon.dbclient.DbClientContext;

/**
 * Helidon DB implementation for JDBC drivers.
 */
class JdbcClient extends DbClientBase implements DbClient {

    private final JdbcConnectionPool connectionPool;

    /**
     * Create a new instance.
     *
     * @param builder builder
     */
    JdbcClient(JdbcClientBuilder builder) {
        super(DbClientContext.builder()
                .statements(builder.statements())
                .dbMapperManager(builder.dbMapperManager())
                .mapperManager(builder.mapperManager())
                .clientServices(builder.clientServices())
                .dbType(builder.connectionPool().dbType())
                .build());
        connectionPool = builder.connectionPool();
    }

    @Override
    public JdbcExecute execute() {
        return new JdbcExecute(context(), connectionPool);
    }

    @Override
    public JdbcTransaction transaction() {
        return new JdbcTransaction(context(), connectionPool);
    }

    @Override
    public String dbType() {
        return connectionPool.dbType();
    }

    @Override
    public <C> C unwrap(Class<C> cls) {
        if (Connection.class.isAssignableFrom(cls)) {
            return cls.cast(connectionPool.connection());
            // JdbcClient and CommonClient unwraps are used in tests.
        } else if (JdbcClient.class.isAssignableFrom(cls) || DbClientBase.class.isAssignableFrom(cls)) {
            return cls.cast(this);
        } else {
            throw new UnsupportedOperationException(String.format("Class %s is not supported for unwrap", cls.getName()));
        }
    }
}
