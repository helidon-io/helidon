/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class MainTest {

    private static final String retryTotal = "ft.io.helidon.tests.integration.restclient.GreetResourceClient." +
            "getDefaultMessage.retry.callsSucceededNotRetried.total";

    @Inject
    private WebTarget target;

    @Inject
    private MetricRegistry registry;

    @Test
    void testHelloWorld() {
        // Get access to @Retry counter
        MetricID id = new MetricID(retryTotal);
        Counter counter = registry.getCounters().get(id);

        // Initially counter must be zero
        assertThat(counter.getCount(), is(0L));

        // Invoke proxy and verify return value
        JsonObject jsonObject = target
                .path("proxy")
                .request()
                .get(JsonObject.class);
        assertThat(jsonObject.getString("message"), is("Hello World!"));

        // Verify that @Retry code was executed by looking at counter
        assertThat(counter.getCount(), is(2L));
    }
}
