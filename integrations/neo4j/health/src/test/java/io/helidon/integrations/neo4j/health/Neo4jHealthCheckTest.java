/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.neo4j.health;

import java.util.Map;

import io.helidon.health.HealthCheckResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.DatabaseInfo;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.ServerInfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Neo4jHealthCheckTest {

    private final Driver driver = mock(Driver.class);
    private final Session session = mock(Session.class);
    private final TransactionContext transaction = mock(TransactionContext.class);

    @BeforeEach
    void setUp() {
        when(driver.session()).thenReturn(session);
        when(session.executeWrite(any())).thenAnswer(invocation -> {
            TransactionCallback<HealthCheckResponse> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        });
    }

    @Test
    void successfulCheckDoesNotExposeServerAddressOrDatabaseName() {
        Result result = mock(Result.class);
        Record record = mock(Record.class);
        ResultSummary summary = mock(ResultSummary.class);
        ServerInfo server = mock(ServerInfo.class);
        DatabaseInfo database = mock(DatabaseInfo.class);
        when(transaction.run(Neo4jHealthCheck.CYPHER)).thenReturn(result);
        when(result.single()).thenReturn(record);
        when(record.get("edition")).thenReturn(Values.value("community"));
        when(result.consume()).thenReturn(summary);
        when(summary.server()).thenReturn(server);
        when(server.protocolVersion()).thenReturn("5.4");
        when(server.address()).thenReturn("private-db.example:7687");
        when(summary.database()).thenReturn(database);
        when(database.name()).thenReturn("internal-payments");

        HealthCheckResponse response = Neo4jHealthCheck.create(driver).call();

        assertThat(response.status(), is(HealthCheckResponse.Status.UP));
        assertThat(response.details(), is(Map.of("server", "5.4", "edition", "community")));
        verify(session).close();
    }

    @Test
    void failedCheckDoesNotExposeExceptionDetails() {
        when(transaction.run(Neo4jHealthCheck.CYPHER)).thenThrow(new IllegalStateException(
                "neo4j://private-db.example:7687/internal-payments?password=health-secret"));

        HealthCheckResponse response = Neo4jHealthCheck.create(driver).call();

        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(response.details(), is(Map.of()));
        verify(session).close();
    }
}
