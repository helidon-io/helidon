/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.reactive.Single;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JdbcClient {

    @Test
    void txErrorHandling() {
        String message = "BOOM IN TX!!!";

        ConnectionPool cPool = Mockito.mock(ConnectionPool.class);
        Mockito.when(cPool.connection()).thenReturn(Mockito.mock(Connection.class));

        JdbcDbClient dbClient = (JdbcDbClient) JdbcDbClientProviderBuilder.create()
                .connectionPool(cPool)
                .build();
        Object result = dbClient.inTransaction(tx -> Single.error(new RuntimeException(message)))
                .onErrorResume(Function.identity())
                .await(200, TimeUnit.MILLISECONDS);

        assertThat(result, CoreMatchers.instanceOf(RuntimeException.class));
        assertThat("Wrong exception propagated.", ((RuntimeException) result).getMessage(), is(equalTo(message)));
    }
}
