/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.mp.jaxrs.preserve.headers;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

@HelidonTest
class TestHeaderPreservation {

    private final WebTarget webTarget;

    @Inject
    TestHeaderPreservation(WebTarget webTarget) {
        this.webTarget = webTarget;
    }

    @Test
    void testHeaderPreservation() {
        Response response = webTarget.path("health").request(MediaType.APPLICATION_JSON_TYPE).get();

        assertThat("Health response status", response.getStatus(), equalTo(200));
        // Without the fix to JaxRsService, the added header will have been removed.
        assertThat("Health response headers",
                   response.getHeaders(),
                   hasKey(HeaderAdjustmentFeatureProvider.ADDED_HEADER.name()));
    }
}
