/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ContextCheckTest extends TestParent {

    @Test
    void testContextCheck() {
        WebClient webClient = createNewClient();
        WebClientResponse r = webClient.get()
                .path("/contextCheck")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request()
                .await();
        assertThat(r.status().code(), is(Http.Status.OK_200.code()));
    }
}
