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

package io.helidon.dbclient.health;

import java.util.Map;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;
import io.helidon.health.HealthCheckResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbClientHealthCheckTest {

    private final DbClient dbClient = mock(DbClient.class);
    private final DbExecute dbExecute = mock(DbExecute.class);

    @BeforeEach
    void setUp() {
        when(dbClient.dbType()).thenReturn("jdbc");
        when(dbClient.execute()).thenReturn(dbExecute);
    }

    @Test
    void successfulPingReportsUp() {
        DbClientHealthCheck check = DbClientHealthCheck.builder(dbClient)
                .dml()
                .statementName("ping")
                .build();

        HealthCheckResponse response = check.call();

        assertThat(response.status(), is(HealthCheckResponse.Status.UP));
        assertThat(response.details(), is(Map.of()));
        verify(dbExecute).namedDml("ping");
    }

    @Test
    void failedPingDoesNotExposeExceptionDetails() {
        when(dbExecute.namedDml("ping")).thenThrow(new IllegalStateException(
                "jdbc:postgresql://private-db.example:5432/payments?user=app&password=health-secret"));
        DbClientHealthCheck check = DbClientHealthCheck.builder(dbClient)
                .dml()
                .statementName("ping")
                .build();

        HealthCheckResponse response = check.call();

        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
        assertThat(response.details(), is(Map.of()));
        assertThat(check.name(), is("jdbc"));
    }
}
