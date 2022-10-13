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

package io.helidon.webserver.examples.comments;

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
import static org.hamcrest.Matchers.isEmptyString;


/**
 * Tests {@link CommentsService}.
 */
public class CommentsServiceTest {

    @Test
    public void addAndGetComments() throws Exception {
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
    public void testRouting() throws Exception {
        Routing routing = Routing.builder()
                .register(new CommentsService())
                .build();
        TestResponse response = TestClient.create(routing)
                .path("one")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));

        response = TestClient.create(routing)
                .path("one")
                .post(MediaPublisher.create(MediaType.TEXT_PLAIN, "aaa"));
        assertThat(response.status(), is(Http.Status.OK_200));

        response = TestClient.create(routing)
                .path("one")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        byte[] data = response.asBytes().toCompletableFuture().join();
        assertThat(new String(data, StandardCharsets.UTF_8), is("anonymous: aaa"));
    }

}
