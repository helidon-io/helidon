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

package io.helidon.webserver.examples.tutorial;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;


/**
 * Tests {@link CommentService}.
 */
@RoutingTest
class CommentServiceTest {

    private final DirectClient client;

    CommentServiceTest(DirectClient client) {
        this.client = client;
    }

    @BeforeAll
    static void setup(HttpRouting.Builder routing) {
        Main.routing(routing);
    }

    @Test
    void addAndGetComments() {
        CommentService service = new CommentService();
        assertThat(service.getComments("one"), empty());
        assertThat(service.getComments("two"), empty());
        service.addComment("one", null, "aaa");
        assertThat(service.getComments("one"), hasSize(1));
        assertThat(service.getComments("two"), empty());
        service.addComment("one", null, "bbb");
        assertThat(service.getComments("one"), hasSize(2));
        assertThat(service.getComments("two"), empty());
        service.addComment("two", null, "bbb");
        assertThat(service.getComments("one"), hasSize(2));
        assertThat(service.getComments("two"), hasSize(1));
    }

    @Test
    void testRouting() {
        try (Http1ClientResponse response = client.get("one").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }

        // Add first comment
        try (Http1ClientResponse response = client.post("one")
                                                  .contentType(HttpMediaType.TEXT_PLAIN)
                                                  .submit("aaa")) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }

        try (Http1ClientResponse response = client.get("one").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.entity().as(String.class), is("anonymous: aaa\n"));
        }


        // Add second comment
        try (Http1ClientResponse response = client.post("one")
                                                  .contentType(HttpMediaType.TEXT_PLAIN)
                                                  .submit("bbb")) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }

        try (Http1ClientResponse response = client.get("one").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.entity().as(String.class), is("anonymous: aaa\nanonymous: bbb\n"));
        }
    }
}
