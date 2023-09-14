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

package io.helidon.examples.webserver.comments;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link Main} class.
 */
@RoutingTest
public class MainTest {

    private final DirectClient client;

    public MainTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
        Main.routing(routing, false);
    }

    @Test
    public void argot() {
        try (Http1ClientResponse response = client.post("/comments/one")
                .header(Main.USER_IDENTITY_HEADER, "Joe")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit("Spring framework is the BEST!")) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }

    @Test
    public void anonymousDisabled() {
        try (Http1ClientResponse response = client.get("/comment/one").request()) {
            assertThat(response.status(), is(Status.FORBIDDEN_403));
        }
    }
}
