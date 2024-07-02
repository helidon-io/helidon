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

package io.helidon.webserver.tests;

import io.helidon.common.GenericType;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Test that methods on entity work as expected.
 */
@ServerTest
class EntityTest {
    private final Http1Client client;

    EntityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        LogConfig.configureRuntime();

        rules.get("/noContent", EntityTest::noContent)
                .get("/content", EntityTest::content);
    }

    @Test
    void testContentAsClassContent() {
        try (Http1ClientResponse response = client.get("/content")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("OK"));
        }
    }

    @Test
    void testContentAsClassContentOptional() {
        try (Http1ClientResponse response = client.get("/content")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().asOptional(String.class), optionalValue(is("OK")));
        }
    }

    @Test
    void testContentAsGenericTypeContent() {
        try (Http1ClientResponse response = client.get("/content")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(GenericType.STRING), is("OK"));
        }
    }

    @Test
    void testContentAsGenericTypeContentOptional() {
        try (Http1ClientResponse response = client.get("/content")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().asOptional(GenericType.STRING), optionalValue(is("OK")));
        }
    }

    @Test
    void testContentAsClassNoContent() {
        try (Http1ClientResponse response = client.get("/noContent")
                .request()) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThrows(IllegalStateException.class, () -> response.entity().as(String.class));
        }
    }

    @Test
    void testContentAsClassNoContentOptional() {
        try (Http1ClientResponse response = client.get("/noContent")
                .request()) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(response.entity().asOptional(String.class), optionalEmpty());
        }
    }

    @Test
    void testContentAsGenericTypeNoContent() {
        try (Http1ClientResponse response = client.get("/noContent")
                .request()) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThrows(IllegalStateException.class, () -> response.entity().as(GenericType.STRING));
        }
    }

    @Test
    void testContentAsGenericTypeNoContentOptional() {
        try (Http1ClientResponse response = client.get("/noContent")
                .request()) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(response.entity().asOptional(GenericType.STRING), optionalEmpty());
        }
    }

    private static void content(ServerRequest req, ServerResponse res) {
        res.status(Status.OK_200).send("OK");
    }

    private static void noContent(ServerRequest req, ServerResponse res) {
        res.status(Status.NO_CONTENT_204).send();
    }

}

