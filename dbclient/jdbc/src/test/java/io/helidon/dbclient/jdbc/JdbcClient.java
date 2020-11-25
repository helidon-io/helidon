/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.reactive.Single;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JdbcClient {

    private static final ConnectionPool POOL = Mockito.mock(ConnectionPool.class);
    private static final Connection CONN = Mockito.mock(Connection.class);
    private static final PreparedStatement PREP_STATEMENT = Mockito.mock(PreparedStatement.class);

    @BeforeAll
    static void beforeAll() throws SQLException {
        Mockito.when(CONN.prepareStatement("SELECT NULL FROM DUAL")).thenReturn(PREP_STATEMENT);
        Mockito.when(POOL.connection()).thenReturn(CONN);
        Mockito.when(PREP_STATEMENT.executeLargeUpdate()).thenReturn(1L);
    }

    @Test
    void txErrorHandling() {
        String message = "BOOM IN TX!!!";

        JdbcDbClient dbClient = (JdbcDbClient) JdbcDbClientProviderBuilder.create()
                .connectionPool(POOL)
                .build();
        Object result = dbClient.inTransaction(tx -> Single.error(new RuntimeException(message)))
                .onErrorResume(Function.identity())
                .await(200, TimeUnit.MILLISECONDS);

        assertThat(result, CoreMatchers.instanceOf(RuntimeException.class));
        assertThat("Wrong exception propagated.", ((RuntimeException) result).getMessage(), is(equalTo(message)));
    }

    @Test
    void txResultHandling() {
        JdbcDbClient dbClient = (JdbcDbClient) JdbcDbClientProviderBuilder.create()
                .connectionPool(POOL)
                .build();
        Object result = dbClient.inTransaction(tx -> tx.dml("SELECT NULL FROM DUAL"))
                .await(200, TimeUnit.MILLISECONDS);

        assertThat(result, is(equalTo(1L)));
    }
}
