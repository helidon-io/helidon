/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import io.helidon.http.Status;
import io.helidon.security.EndpointConfig;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link WebClientSecurity}.
 */
public class SecurityTest extends TestParent {

    SecurityTest(WebServer server) {
        super(server);
    }

    @Test
    void testBasic() {
        performOperation("/secure/basic");
    }

    @Test
    void testBasicOutbound() {
        //This test tests whether server security is properly propagated to client
        performOperation("/secure/basic/outbound");
    }

    private void performOperation(String path) {
        try (Http1ClientResponse response = client.get()
                .path(path)
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jack")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "password")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(JsonObject.class).getString("message"), is("Hello jack!"));
        }
    }

}
