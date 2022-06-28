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

package io.helidon.webserver;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests correct behavior when webserver is shutdown and an attempt is made to start it again.
 * Github issue #377.
 */
class Gh377Test {
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void testRestart() {
        WebServer webServer = WebServer.builder()
                .defaultSocket(s -> s.host("localhost"))
                .routing(r -> r.get("/", (req, res) -> res.send("Hello World")))
                .build()
                .start()
                .await(TIMEOUT);

        assertThat(webServer.port(), greaterThan(0));

        // shutdown
        webServer.shutdown()
                .await(TIMEOUT);

        assertThat(webServer.port(), is(-1));

        // attempt to start again
        assertThrows(IllegalStateException.class, () -> webServer.start()
                .await(TIMEOUT));

        assertThat(webServer.port(), is(-1));
    }
}
