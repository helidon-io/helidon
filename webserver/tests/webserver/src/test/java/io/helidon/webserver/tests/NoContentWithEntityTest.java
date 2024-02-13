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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class NoContentWithEntityTest {
    private final Http1Client client;

    NoContentWithEntityTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/noContent", NoContentWithEntityTest::noContent)
                .post("/noContentStream", NoContentWithEntityTest::noContentStream)
                .post("/ok", NoContentWithEntityTest::ok);

        LogConfig.configureRuntime();
    }

    @Test
    void testNoContentWorksFollowedByOk() {
        try (Http1ClientResponse response = client.post("/noContent")
                .submit("text")) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
        }

        ClientResponseTyped<String> okResponse = client.post("/ok")
                .submit("text", String.class);

        assertThat(okResponse.status(), is(Status.OK_200));
        assertThat(okResponse.entity(), is("text"));
    }

    @Test
    void testNoContentWorksFollowedByOkStreaming() {
        try (Http1ClientResponse response = client.post("/noContentStream")
                .submit("text")) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
        }

        ClientResponseTyped<String> okResponse = client.post("/ok")
                .submit("text", String.class);

        assertThat(okResponse.status(), is(Status.OK_200));
        assertThat(okResponse.entity(), is("text"));
    }

    private static void ok(ServerRequest req, ServerResponse res) {
        res.status(Status.OK_200).send("text");
    }

    private static void noContentStream(ServerRequest req, ServerResponse res) throws IOException {
        res.status(Status.NO_CONTENT_204);
        try (OutputStream out = res.outputStream()) {
            out.write("text".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void noContent(ServerRequest req, ServerResponse res) {
        res.status(Status.NO_CONTENT_204).send("text");
    }

}

