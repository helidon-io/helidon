/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ResponseListenerTest {
    private static final AtomicBoolean BEFORE_SEND_CALLED = new AtomicBoolean(false);
    private static final AtomicBoolean WHEN_SENT_CALLED = new AtomicBoolean(false);
    private static final AtomicBoolean WHEN_BEFORE_SEND = new AtomicBoolean(false);

    private final Http1Client client;

    ResponseListenerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> {
            res.beforeSend(() -> {
                        res.status(Status.PARTIAL_CONTENT_206);
                        BEFORE_SEND_CALLED.set(true);
                        if (WHEN_SENT_CALLED.get()) {
                            WHEN_BEFORE_SEND.set(true);
                        }
                    })
                    .whenSent(() -> WHEN_SENT_CALLED.set(true));

            res.send("done");
        });
    }

    @AfterEach
    void afterEach() {
        WHEN_SENT_CALLED.set(false);
        WHEN_BEFORE_SEND.set(false);
        BEFORE_SEND_CALLED.set(false);
    }

    @Test
    void testCalls() {
        var response = client.get("/").request(String.class);

        assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
        assertThat(response.entity(), is("done"));

        assertThat("whenSent called before beforeSend", WHEN_BEFORE_SEND.get(), is(false));
        assertThat("beforeSend not called", BEFORE_SEND_CALLED.get(), is(true));
        assertThat("whenSent not called", WHEN_SENT_CALLED.get(), is(true));
    }
}
