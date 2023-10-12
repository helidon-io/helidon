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

package io.helidon.webserver.cors;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class TestHandlerRegistration extends CorsRouting {

    static final String CORS4_CONTEXT_ROOT = "/cors4";

    private final Http1Client client;

    TestHandlerRegistration(Http1Client client) {
        this.client = client;
    }

    @Test
    void test4PreFlightAllowedHeaders2() {
        try (HttpClientResponse response = client.method(Method.OPTIONS)
                .uri(CORS4_CONTEXT_ROOT)
                .header(ORIGIN, "http://foo.bar")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-foo, X-bar")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "http://foo.bar"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "PUT"));
            assertThat(response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-foo"));
            assertThat(response.headers().get(ACCESS_CONTROL_ALLOW_HEADERS).values(), containsString("X-bar"));
        }
    }
}
