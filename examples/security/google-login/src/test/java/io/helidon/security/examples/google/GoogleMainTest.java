/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.security.examples.google;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Google login common unit tests.
 */
public abstract class GoogleMainTest {
    private final Http1Client client;

    GoogleMainTest(Http1Client client) {
        this.client = client;
    }

    @Test
    public void testEndpoint() {
        try (Http1ClientResponse response = client.get("/rest/profile").request()) {

            assertThat(response.status(), is(Http.Status.UNAUTHORIZED_401));
            assertThat(response.headers().first(Http.HeaderNames.WWW_AUTHENTICATE),
                    optionalValue(is("Bearer realm=\"helidon\",scope=\"openid profile email\"")));
        }
    }
}
