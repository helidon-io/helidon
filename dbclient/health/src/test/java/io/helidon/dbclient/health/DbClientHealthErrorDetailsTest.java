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

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Single;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbExecute;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbClientHealthErrorDetailsTest {

    private final DbClient dbClient = mock(DbClient.class);
    private final DbExecute dbExecute = mock(DbExecute.class);

    @BeforeEach
    void setUp() {
        when(dbClient.dbType()).thenReturn("jdbc");
        when(dbClient.<Long, Single<Long>>execute(any())).thenAnswer(invocation -> {
            Function<DbExecute, Single<Long>> executor = invocation.getArgument(0);
            return executor.apply(dbExecute);
        });
    }

    @Test
    void successfulPingReportsUp() {
        when(dbExecute.namedDml("ping")).thenReturn(Single.just(1L));
        DbClientHealthCheck check = DbClientHealthCheck.builder(dbClient)
                .dml()
                .statementName("ping")
                .build();

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus(), is(HealthCheckResponse.Status.UP));
        assertThat(response.getData(), is(Optional.empty()));
        assertThat(response.getName(), is("jdbc"));
        verify(dbExecute).namedDml("ping");
    }

    @Test
    void failedPingDoesNotExposeExceptionDetails() {
        when(dbExecute.namedDml("ping")).thenReturn(Single.error(new IllegalStateException(
                "jdbc:postgresql://private-db.example:5432/payments?user=app&password=health-secret")));
        DbClientHealthCheck check = DbClientHealthCheck.builder(dbClient)
                .dml()
                .statementName("ping")
                .build();

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus(), is(HealthCheckResponse.Status.DOWN));
        assertThat(response.getData(), is(Optional.empty()));
        assertThat(response.getName(), is("jdbc"));
        verify(dbExecute).namedDml("ping");
    }
}
