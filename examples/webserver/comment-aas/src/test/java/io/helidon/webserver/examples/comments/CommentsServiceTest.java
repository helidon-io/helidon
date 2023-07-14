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

package io.helidon.webserver.examples.comments;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.nima.testing.junit5.webserver.DirectClient;
import io.helidon.nima.testing.junit5.webserver.RoutingTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;


/**
 * Tests {@link CommentsService}.
 */
@RoutingTest
public class CommentsServiceTest {

    private final DirectClient client;

    public CommentsServiceTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
        Main.routing(routing, false);
    }

    @Test
    public void addAndGetComments() {
        CommentsService service = new CommentsService();
        assertThat(service.listComments("one"), isEmptyString());
        assertThat(service.listComments("two"), isEmptyString());

        service.addComment("aaa", null, "one");
        assertThat(service.listComments("one"), is("anonymous: aaa"));
        assertThat(service.listComments("two"), isEmptyString());

        service.addComment("bbb", "Foo", "one");
        assertThat(service.listComments("one"), is("anonymous: aaa\nFoo: bbb"));
        assertThat(service.listComments("two"), isEmptyString());

        service.addComment("bbb", "Bar", "two");
        assertThat(service.listComments("one"), is("anonymous: aaa\nFoo: bbb"));
        assertThat(service.listComments("two"), is("Bar: bbb"));
    }

    @Test
    public void testRouting() {
        try (Http1ClientResponse response = client.get("one").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
        }
        try (Http1ClientResponse response = client.post("one")
                                                  .contentType(HttpMediaType.TEXT_PLAIN)
                                                  .submit("aaa")) {

            assertThat(response.status(), is(Http.Status.OK_200));
        }

        try (Http1ClientResponse response = client.get("one").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.entity().as(String.class), is("anonymous: aaa"));
        }
    }

}
