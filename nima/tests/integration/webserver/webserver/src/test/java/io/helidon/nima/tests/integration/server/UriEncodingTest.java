/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SocketHttpClient;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

@ServerTest
class UriEncodingTest {
    private final SocketHttpClient socketHttpClient;

    UriEncodingTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/foo", (req, res) -> res.send("It works!"))
                .get("/foo/{bar}", (req, res) -> res.send(req.path().templateParameters().value("bar")));
    }

    /**
     * Test path decoding and matching.
     */
    @Test
    void testEncodedUrl() {
        String s = socketHttpClient.sendAndReceive("/f%6F%6F", Http.Method.GET, null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("It works!"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase("connection"), is("keep-alive")));
    }

    /**
     * Test path decoding with params and matching.
     */
    @Test
    void testEncodedUrlParams() {
        String s = socketHttpClient.sendAndReceive("/f%6F%6F/b%61%72", Http.Method.GET, null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("bar"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase("connection"), is("keep-alive")));
    }
}
