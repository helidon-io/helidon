/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Tests {@link CommentService}.
 */
public class CommentServiceTest {

    @Test
    public void addAndGetComments() throws Exception {
        CommentService service = new CommentService();
        assertEquals(0, service.getComments("one").size());
        assertEquals(0, service.getComments("two").size());
        service.addComment("one", null, "aaa");
        assertEquals(1, service.getComments("one").size());
        assertEquals(0, service.getComments("two").size());
        service.addComment("one", null, "bbb");
        assertEquals(2, service.getComments("one").size());
        assertEquals(0, service.getComments("two").size());
        service.addComment("two", null, "bbb");
        assertEquals(2, service.getComments("one").size());
        assertEquals(1, service.getComments("two").size());
    }

    @Test
    public void testRouting() throws Exception {
        Routing routing = Routing.builder()
                .register(new CommentService())
                .build();
        TestResponse response = TestClient.create(routing)
                                        .path("one")
                                        .get();
        assertEquals(Http.Status.OK_200, response.status());

        // Add first comment
        response = TestClient.create(routing)
                .path("one")
                .post(MediaPublisher.of(MediaType.TEXT_PLAIN, "aaa"));
        assertEquals(Http.Status.OK_200, response.status());
        response = TestClient.create(routing)
                .path("one")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        byte[] data = response.asBytes().toCompletableFuture().get();
        assertEquals("anonymous: aaa\n", new String(data, StandardCharsets.UTF_8));

        // Add second comment
        response = TestClient.create(routing)
                .path("one")
                .post(MediaPublisher.of(MediaType.TEXT_PLAIN, "bbb"));
        assertEquals(Http.Status.OK_200, response.status());
        response = TestClient.create(routing)
                .path("one")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        data = response.asBytes().toCompletableFuture().get();
        assertEquals("anonymous: aaa\nanonymous: bbb\n", new String(data, StandardCharsets.UTF_8));
    }
}
