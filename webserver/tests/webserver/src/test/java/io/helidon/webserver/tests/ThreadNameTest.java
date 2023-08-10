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

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;

@ServerTest
class ThreadNameTest {
    private final Http1Client client;

    ThreadNameTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/", (req, res) -> {
            String socketId = req.serverSocketId();
            String clientSocketId = req.socketId();
            boolean isVirtual = Thread.currentThread().isVirtual();

            res.send(isVirtual + ":" + socketId + ":" + clientSocketId + ":" + Thread.currentThread().getName());
        });
    }

    @Test
    void testName() {
        String message = client.get()
                .requestEntity(String.class);
        String[] parts = message.split(":");
        // the parts must be 4 long
        assertThat("Response should have four parts: isVirtual,serverSocketId,connSocketId,threadName", parts, arrayWithSize(4));
        assertThat("Thread must be virtual", parts[0], is("true"));
        String serverSocket = parts[1];
        String clientSocket = parts[2];

        assertThat(parts[3], is("[" + serverSocket + " " + clientSocket + "] WebServer socket"));
    }
}
