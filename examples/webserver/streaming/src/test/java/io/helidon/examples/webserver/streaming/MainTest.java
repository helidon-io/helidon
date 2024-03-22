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

package io.helidon.examples.webserver.streaming;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MainTest {

    private final Http1Client client;

    private final String TEST_DATA_1 = "Test Data 1";
    private final String TEST_DATA_2 = "Test Data 2";

    protected MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    @Order(0)
    void testBadRequest() {
        try (Http1ClientResponse response = client.get("/download").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
        }
    }

    @Test
    @Order(1)
    void testUpload1() {
        try (Http1ClientResponse response = client.post("/upload").submit(TEST_DATA_1)) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    @Order(2)
    void testDownload1() {
        try (Http1ClientResponse response = client.get("/download").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(TEST_DATA_1));
        }
    }

    @Test
    @Order(3)
    void testUpload2() {
        try (Http1ClientResponse response = client.post("/upload").submit(TEST_DATA_2)) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    @Order(4)
    void testDownload2() {
        try (Http1ClientResponse response = client.get("/download").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(TEST_DATA_2));
        }
    }
}
