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

package io.helidon.examples.webserver.threads;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MainTest {
    private final WebClient client;

    protected MainTest(WebClient client) {
        this.client = client;
        Main.webclient = this.client; // Needed for ThreadService to make calls
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    void testFanOut() {
        try (HttpClientResponse response = client.get("/thread/fanout/2").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void testCompute() {
        try (HttpClientResponse response = client.get("/thread/compute").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }
}