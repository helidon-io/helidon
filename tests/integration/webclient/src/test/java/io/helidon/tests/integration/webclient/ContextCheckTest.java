/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webclient;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ContextCheckTest extends TestParent {

    ContextCheckTest(WebServer server, Http1Client client) {
        super(server, client);
    }

    @Test
    void testContextCheck() {
        Http1Client webClient = createNewClient();
        try (Http1ClientResponse r = webClient.get()
                .path("/contextCheck")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request()) {
            assertThat(r.status().code(), is(Http.Status.OK_200.code()));
        }
    }
}
