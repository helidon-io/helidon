/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.service.employee;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import jakarta.json.JsonArray;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
public class MainTest {

    private final DirectClient client;

    public MainTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    public static void setup(HttpRouting.Builder routing) {
        Main.routing(routing, Config.empty());
    }

    @Test
    public void testEmployees() {
        try (Http1ClientResponse response = client.get("/employees")
                                                  .request()) {
            assertThat("HTTP response2", response.status(), is(Http.Status.OK_200));
            assertThat(response.as(JsonArray.class).size(), is(40));
        }
    }

}
