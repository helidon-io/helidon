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
package io.helidon.microprofile.metrics;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

/**
 * Makes sure that the vetoed resource's metric is not registered.
 */
@HelidonTest
class TestVetoedResource {

    @Inject
    private WebTarget webTarget;

    @Inject
    MetricRegistry registry;

    @Test
    void testVetoedResource() {
        Response res = webTarget.path("/vetoed")
                .request()
                .get();
        // JAX-RS should not handle the endpoint (because its extension should be sensitive to vetoes).
        assertThat(res.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

        // The metrics CDI extension should ignore the vetoed resource's metrics.
        MetricID vetoedID = new MetricID(VetoedResource.COUNTER_NAME);
        assertThat("Metrics CDI extension incorrectly registered a metric on a vetoed resource",
                registry.getCounters().containsKey(vetoedID), is(false));
    }
}
