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
import io.helidon.security.EndpointConfig;
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
                .property(EndpointConfig.PROPERTY_OUTBOUND_ID, "jack")
                .property(EndpointConfig.PROPERTY_OUTBOUND_SECRET, "password")
                .request()
                .await();
        assertThat(r.status().code(), is(Http.Status.OK_200.code()));
    }
}
