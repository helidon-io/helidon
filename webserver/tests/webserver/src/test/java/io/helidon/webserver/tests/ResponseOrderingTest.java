/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;

/**
 * The ResponseOrderingTest tests whether http chunks were sent in a correct order which was reported as MIC-6419.
 * Note that in order to better reproduce the original intermittent failures, {@code REQUESTS_COUNT}
 * environment variable needs to be set to {@code 1000} at least.
 */
@ServerTest
class ResponseOrderingTest {

    private static ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();
    private static ConcurrentLinkedQueue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();

    private final Http1Client client;

    ResponseOrderingTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.any("/multi", (req, res) -> {
                    long requestId = Long.parseLong(req.query().get("id"));
                    queue.add(requestId);
                    res.status(Status.CREATED_201)
                            .send("" + requestId);
                })
                .any("/stream", (req, res) -> {
                    try (InputStream in = req.content().inputStream();
                            OutputStream out = res.outputStream()) {
                        in.transferTo(out);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    @Test
    void testOrdering() {
        ArrayList<Long> returnedIds = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            returnedIds.add(Long.parseLong(client.get("/multi")
                                                   .queryParam("id", String.valueOf(i))
                                                   .requestEntity(String.class)));
        }

        assertThat(returnedIds.toArray(), allOf(arrayWithSize(1000), is(queue.toArray())));
    }

    @Test
    void testContentOrdering() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append(i)
                    .append("\n");
        }

        String response = client.method(Method.POST)
                .path("/stream")
                .submit(sb.toString().getBytes())
                .as(String.class);

        assertThat(response, is(sb.toString()));
    }
}
