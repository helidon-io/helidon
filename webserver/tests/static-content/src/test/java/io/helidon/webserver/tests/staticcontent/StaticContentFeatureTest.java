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

package io.helidon.webserver.tests.staticcontent;

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StaticContentFeatureTest {
    private static final String STATIC_CONTENT = "/staticcontent";
    private static final String ENTITY = "entity";

    @Test
    void testDefaultWeight() {
        test(95, ENTITY);
    }

    @Test
    void testHigherWeight() {
        test(101, "Welcome");
    }

    void test(double weight, String expected) {
        WebServer server = WebServer.builder()
                .addFeature(StaticContentFeature.builder()
                                    .addClasspath(cl -> cl.location("static")
                                            .context(STATIC_CONTENT)
                                            .welcome("welcome.txt"))
                                    .weight(weight)
                                    .build())
                .addRouting(HttpRouting.builder()
                                    .get(STATIC_CONTENT, (req, res) -> res.send(ENTITY)))
                .port(0)
                .build()
                .start();

        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .build();

        String response = client.get(STATIC_CONTENT).requestEntity(String.class);
        assertThat(response, is(expected));

        server.stop();
    }
}
