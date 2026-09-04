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

package io.helidon.http.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.MeterRegistry;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportMetricsTest {
    @Test
    void releasesNoOpMeterBindingsAfterEachLease() throws Exception {
        AtomicInteger added = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        MeterRegistry registry = MeterRegistry.create()
                .onMeterAdded(_ -> added.incrementAndGet())
                .onMeterRemoved(_ -> removed.incrementAndGet());

        for (int i = 0; i < 2; i++) {
            int addedBefore = added.get();
            int removedBefore = removed.get();
            HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
            try (lease) {
                lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            }
            lease.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(added.get() - addedBefore,
                         removed.get() - removedBefore,
                         "Each no-op meter should be released only in its owning cycle");
            assertTrue(registry.meters().isEmpty());
        }
    }

}
