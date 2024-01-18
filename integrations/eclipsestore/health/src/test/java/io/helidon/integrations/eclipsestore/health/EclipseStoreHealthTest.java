/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.eclipsestore.health;

import io.helidon.health.HealthCheckResponse;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class EclipseStoreHealthTest {
    private EmbeddedStorageManager embeddedStorageManager;

    @BeforeEach
    void init() {
        embeddedStorageManager = Mockito.mock(EmbeddedStorageManager.class);
    }

    private void setEclipseStoreStatus(boolean isRunning) {
        Mockito.when(embeddedStorageManager.isRunning()).thenReturn(isRunning);
    }

    @Test
    void testStatusRunning() {
        setEclipseStoreStatus(true);
        EclipseStoreHealthCheck check = EclipseStoreHealthCheck.create(embeddedStorageManager);
        HealthCheckResponse response = check.call();
        assertThat(response.status(), is(HealthCheckResponse.Status.UP));
    }

    @Test
    void testStatusNotRunning() {
        setEclipseStoreStatus(false);
        EclipseStoreHealthCheck check = EclipseStoreHealthCheck.create(embeddedStorageManager);
        HealthCheckResponse response = check.call();
        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
    }

    @Test
    void testStatusTimeout() {

        Mockito.when(embeddedStorageManager.isRunning()).then((x) -> {
            Thread.sleep(100);
            return true;
        });

        EclipseStoreHealthCheck check = EclipseStoreHealthCheck
                .builder(embeddedStorageManager)
                .timeout(Duration.of(20, ChronoUnit.MILLIS))
                .build();

        HealthCheckResponse response = check.call();
        assertThat(response.status(), is(HealthCheckResponse.Status.DOWN));
    }
}
