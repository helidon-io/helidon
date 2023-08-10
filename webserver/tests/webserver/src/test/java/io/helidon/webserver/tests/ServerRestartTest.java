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

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerRestartTest {
    @Test
    void testRestart() {
        WebServer server = WebServer.builder()
                .host("127.0.0.1")
                .routing(http -> http.get("/", (req, res) -> res.send("Hello World!")))
                .build()
                .start();

        assertThat(server.port(), greaterThan(0));
        server.stop();
        assertThrows(IllegalStateException.class, server::start);
        assertThat(server.port(), is(-1));
    }
}
