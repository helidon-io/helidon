/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class UriEncodingTest {
    private final SocketHttpClient socketHttpClient;

    UriEncodingTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/foo", (req, res) -> res.send("It works!"))
                .get("/foo/{bar}", (req, res) -> res.send(req.path().pathParameters().get("bar")));
    }

    /**
     * Test path decoding and matching.
     */
    @Test
    void testEncodedUrl() {
        String s = socketHttpClient.sendAndReceive(Method.GET, "/f%6F%6F", null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("It works!"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
    }

    /**
     * Test path decoding with params and matching.
     */
    @Test
    void testEncodedUrlParams() {
        String s = socketHttpClient.sendAndReceive(Method.GET, "/f%6F%6F/b%61%72", null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("bar"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
    }
}
