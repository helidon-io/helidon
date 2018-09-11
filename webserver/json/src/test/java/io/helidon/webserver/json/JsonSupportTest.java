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

package io.helidon.webserver.json;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.MediaPublisher;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link JsonSupport}.
 */
public class JsonSupportTest {

    private JsonObject createJson() {
        return Json.createObjectBuilder()
                .add("a", "aaa")
                .add("b", 3)
                .build();
    }

    @Test
    public void pingPong() throws Exception {
        Routing routing = Routing.builder()
                                 .register(JsonSupport.get())
                                 .post("/foo", Handler.of(JsonObject.class, (req, res, json) -> res.send(json)))
                                 .build();
        JsonObject json = createJson();
        TestResponse response = TestClient.create(routing)
                                          .path("/foo")
                                          .post(MediaPublisher
                                                        .of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));

        assertEquals(MediaType.APPLICATION_JSON.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
        byte[] bytes = response.asBytes().toCompletableFuture().get(10, TimeUnit.SECONDS);
        JsonObject json2 = Json.createReader(new ByteArrayInputStream(bytes)).readObject();
        assertEquals(json, json2);
    }

    @Test
    public void invalidJson() throws Exception {
        Routing routing = Routing.builder()
                .register(JsonSupport.get())
                .post("/foo", Handler.of(JsonObject.class, (req, res, json) -> res.send(json)))
                .build();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), "{ ... invalid ... }"));

        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, response.status());
    }

    @Test
    public void explicitJsonSupportRegistrationMissingJsonProperty() throws Exception {
        Routing routing = Routing.builder()
                .post("/foo", Handler.of(JsonObject.class, (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));

        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, response.status());
    }

    @Test
    public void acceptHeaders() throws Exception {
        Routing routing = Routing.builder()
                .register(JsonSupport.get())
                .post("/foo", Handler.of(JsonObject.class, (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();

        // Has accept
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/json; q=.1")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals(MediaType.APPLICATION_JSON.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));

        // Has accept with +json
        response = TestClient.create(routing)
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/specific+json; q=.1")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals(MediaType.parse("application/specific+json").toString(),
                     response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));

        // With start
        response = TestClient.create(routing)
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/*; q=.1")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals(MediaType.APPLICATION_JSON.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));

        // With JOSNP standard application/javascript
        response = TestClient.create(routing)
                .path("/foo")
                .header("Accept", "application/javascript")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("application/javascript", response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));

        // Without start
        response = TestClient.create(routing)
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/specific; q=.1")
                .post(MediaPublisher.of(MediaType.APPLICATION_JSON.withCharset("UTF-8"), json.toString()));
        assertEquals(Http.Status.INTERNAL_SERVER_ERROR_500, response.status());
    }
}
