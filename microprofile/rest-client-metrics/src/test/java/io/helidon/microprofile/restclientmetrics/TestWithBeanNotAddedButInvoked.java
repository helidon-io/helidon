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

package io.helidon.microprofile.restclientmetrics;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.Test;

@HelidonTest
class TestWithBeanNotAddedButInvoked {

    @Inject
    private WebTarget webTarget;

    @Test
    void testServiceClientWhenNotAdded() {
        // Following should not fail, even though the CDI extension was not notified of the type
        // because we did not add it as a bean. This simulates some tests in the MP REST client TCK.
        RestClientBuilder.newBuilder()
                .baseUri(webTarget.getUri())
                .build(TestScanning.ServiceClient.class);
    }

    @Path("/")
    interface ServiceClient {
    }
}
