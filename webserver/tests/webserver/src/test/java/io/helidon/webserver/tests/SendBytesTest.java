/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests sending a part of a byte array.
 */
@ServerTest
class SendBytesTest {
    private static final int START = 16;
    private static final int LENGTH = 9;
    private static final String ENTITY = "The quick brown fox jumps over the lazy dog";

    private final Http1Client http1Client;

    SendBytesTest(Http1Client http1Client) {
        this.http1Client = http1Client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sendAll", (req, res) ->
                        res.send(ENTITY.getBytes(StandardCharsets.UTF_8)))
                .get("/sendPart", (req, res) ->
                        res.send(ENTITY.getBytes(StandardCharsets.UTF_8), START, LENGTH));
    }

    /**
     * Test getting all the entity.
     */
    @Test
    void testAll() {
        try (HttpClientResponse r = http1Client.get("/sendAll").request()) {
            String s = r.entity().as(String.class);
            assertThat(r.status(), is(Status.OK_200));
            assertThat(s, is(ENTITY));
        }
    }

    /**
     * Test getting part of the entity.
     */
    @Test
    void testPart() {
        try (HttpClientResponse r = http1Client.get("/sendPart").request()) {
            String s = r.entity().as(String.class);
            assertThat(r.status(), is(Status.OK_200));
            assertThat(s, is(ENTITY.substring(START, START + LENGTH)));
        }
    }
}
