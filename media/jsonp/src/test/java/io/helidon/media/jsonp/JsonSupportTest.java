/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.jsonp;

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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link JsonpSupport}.
 */
class JsonSupportTest {

    private JsonObject createJson() {
        return Json.createObjectBuilder()
                .add("a", "aaa")
                .add("b", 3)
                .build();
    }

    @Test
    public void defaultJsonSupportAsSingleton() {
        assertThat(JsonpSupport.create(), is(sameInstance(JsonpSupport.create())));
    }

    @Test
    public void pingPong() throws Exception {
        Routing routing = Routing.builder()
                .post("/foo", Handler.create(JsonObject.class,
                        (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();
        TestResponse response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));

        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(equalTo(MediaType.APPLICATION_JSON.toString())));

        byte[] bytes = response.asBytes().toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        JsonObject json2 = Json.createReader(new ByteArrayInputStream(bytes))
                .readObject();
        assertThat(json2, is(equalTo(json)));
    }

    @Test
    public void pingPongNoCharset() throws Exception {
        Routing routing = Routing.builder()
                .post("/foo", Handler.create(JsonObject.class,
                        (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();
        TestResponse response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .post(MediaPublisher.create(MediaType.APPLICATION_JSON,
                        json.toString()));

        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(equalTo(MediaType.APPLICATION_JSON.toString())));
        byte[] bytes = response.asBytes().toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        JsonObject json2 = Json.createReader(new ByteArrayInputStream(bytes))
                .readObject();
        assertThat(json2, is(equalTo(json)));
    }

    @Test
    public void invalidJson() throws Exception {
        Routing routing = Routing.builder()
                .post("/foo", Handler.create(JsonObject.class,
                        (req, res, json) -> res.send(json)))
                .build();
        TestResponse response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        "{ ... invalid ... }"));

        assertThat(response.status(), is(equalTo(Http.Status.INTERNAL_SERVER_ERROR_500)));
    }

    @Test
    public void explicitJsonSupportRegistrationMissingJsonProperty()
            throws Exception {

        Routing routing = Routing.builder()
                .post("/foo", Handler.create(JsonObject.class,
                        (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();
        TestResponse response = TestClient.create(routing)
                .path("/foo")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));

        assertThat(response.status(), is(equalTo(Http.Status.INTERNAL_SERVER_ERROR_500)));
    }

    @Test
    public void acceptHeaders() throws Exception {
        Routing routing = Routing.builder()
                .post("/foo", Handler.create(JsonObject.class,
                        (req, res, json) -> res.send(json)))
                .build();
        JsonObject json = createJson();

        // Has accept
        TestResponse response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/json; q=.1")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));
        assertThat(response.status(), is(equalTo(Http.Status.OK_200)));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(equalTo(MediaType.APPLICATION_JSON.toString())));

        // Has accept with +json
        response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/specific+json; q=.1")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));
        assertThat(response.status(), is(equalTo(Http.Status.OK_200)));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(equalTo(MediaType.parse("application/specific+json").toString())));

        // With start
        response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/*; q=.1")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));
        assertThat(response.status(), is(equalTo(Http.Status.OK_200)));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null),
                is(equalTo(MediaType.APPLICATION_JSON.toString())));

        // With JSONP standard application/javascript
        response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .header("Accept", "application/javascript")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));
        assertThat(response.status(),
                is(equalTo(Http.Status.INTERNAL_SERVER_ERROR_500)));

        // Without start
        response = TestClient.create(routing, JsonpSupport.create())
                .path("/foo")
                .header("Accept", "text/plain; q=.8, application/specific; q=.1")
                .post(MediaPublisher.create(
                        MediaType.APPLICATION_JSON.withCharset("UTF-8"),
                        json.toString()));
        assertThat(response.status(),
                is(equalTo(Http.Status.INTERNAL_SERVER_ERROR_500)));
    }
}
