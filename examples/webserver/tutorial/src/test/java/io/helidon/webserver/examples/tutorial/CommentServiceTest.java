/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.MediaPublisher;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;


/**
 * Tests {@link CommentService}.
 */
public class CommentServiceTest {

    @Test
    public void addAndGetComments() throws Exception {
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
    public void testRouting() throws Exception {
        Routing routing = Routing.builder()
                .register(new CommentService())
                .build();
        TestResponse response = TestClient.create(routing)
                                        .path("one")
                                        .get();
        assertThat(response.status(), is(Http.Status.OK_200));

        // Add first comment
        response = TestClient.create(routing)
                .path("one")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertThat(response.status(), is(Http.Status.OK_200));
        response = TestClient.create(routing)
                .path("one")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        byte[] data = response.asBytes().toCompletableFuture().get();
        assertThat(new String(data, StandardCharsets.UTF_8), is("anonymous: aaa\n"));

        // Add second comment
        response = TestClient.create(routing)
                .path("one")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "bbb"));
        assertThat(response.status(), is(Http.Status.OK_200));
        response = TestClient.create(routing)
                .path("one")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        data = response.asBytes().toCompletableFuture().get();
        assertThat(new String(data, StandardCharsets.UTF_8), is("anonymous: aaa\nanonymous: bbb\n"));
    }
}
