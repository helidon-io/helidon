/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver.cors;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.webclient.ClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;

import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.common.http.Http.Header.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class CorsTest extends AbstractCorsTest {
    private static final String CONTEXT_ROOT = "/greet";
    private final Http1Client client;

    CorsTest(Http1Client client) {
        this.client = client;
    }

    @Override
    String contextRoot() {
        return CONTEXT_ROOT;
    }

    @Override
    Http1Client client() {
        return client;
    }

    @Override
    String fooOrigin() {
        return "http://foo.bar";
    }

    @Override
    String fooHeader() {
        return "X-foo";
    }

    @Test
    void test1PreFlightAllowedOrigin() {
        String origin = fooOrigin();
        ClientResponse response = runTest1PreFlightAllowedOrigin();

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
        assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_HEADERS));
        assertThat(response.headers(), hasHeader(ACCESS_CONTROL_MAX_AGE, "3600"));
        assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_ORIGIN).allValues().size(), is(1));
    }
}
