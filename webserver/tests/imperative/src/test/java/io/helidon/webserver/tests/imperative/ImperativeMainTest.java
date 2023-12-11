/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.imperative;

import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ImperativeMainTest {
    private final Http1Client mainClient;
    private final Http1Client adminClient;

    ImperativeMainTest(Http1Client mainClient, @Socket("admin") Http1Client adminClient) {
        this.mainClient = mainClient;
        this.adminClient = adminClient;
    }

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder routing) {
        ImperativeMain.routing(routing);
    }

    @SetUpRoute("admin")
    static void setUpRouteAdmin(HttpRouting.Builder routing) {
        ImperativeMain.routing(routing);
    }

    @Test
    void testMainSocket() {
        testSocket(mainClient);
    }

    @Test
    void testAdminSocket() {
        testSocket(adminClient);
    }

    private void testSocket(Http1Client client) {
        String response = client.get()
                .requestEntity(String.class);

        assertThat(response, is("Hello World!"));
    }
}