/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.restclient;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@HelidonTest
class MainTest {

    private static final String retryTotal = "ft.retry.calls.total";

    @Inject
    private WebTarget target;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry registry;

    @Test
    void testHelloWorld() {
        Tag method = new Tag("method",
                "io.helidon.tests.integration.restclient.GreetResourceClient.getDefaultMessage");
        Tag retried = new Tag("retried", "false");
        Tag retryResult = new Tag("retryResult", "valueReturned");
        MetricID metricID = new MetricID(retryTotal, method, retried, retryResult);

        // Counter should be undefined at this time
        Counter counter = registry.getCounters().get(metricID);
        assertNull(counter);

        // Invoke proxy and verify return value
        JsonObject jsonObject = target
                .path("proxy")
                .request()
                .get(JsonObject.class);
        assertEquals("Hello World!", jsonObject.getString("message"));

        // Verify that @Retry code was executed by looking at counter
        counter = registry.getCounters().get(metricID);
        assertEquals(2L, counter.getCount());
    }
}
