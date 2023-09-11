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

package io.helidon.examples.webserver.basics;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderName;
import io.helidon.http.Http;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaContextConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Status.BAD_REQUEST_400;
import static io.helidon.http.Status.CREATED_201;
import static io.helidon.http.Status.INTERNAL_SERVER_ERROR_500;
import static io.helidon.http.Status.OK_200;
import static io.helidon.http.Status.PRECONDITION_FAILED_412;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class MainTest {

    private static final HeaderName FOO_HEADER = Http.HeaderNames.create("foo");

    private final Http1Client client;

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        MediaContextConfig.Builder mediaContext = MediaContext.builder()
                        .mediaSupportsDiscoverServices(false);
        server.routing(routing -> {
            Main.firstRouting(routing);
            Main.mediaReader(routing, mediaContext);
            Main.advancedRouting(routing);
            Main.organiseCode(routing);
            Main.routingAsFilter(routing);
            Main.parametersAndHeaders(routing);
            Main.errorHandling(routing);
            Main.readContentEntity(routing);
            Main.supports(routing, mediaContext);
        });
        server.mediaContext(mediaContext.build());
    }

    @Test
    public void firstRouting() {
        // POST
        try (Http1ClientResponse response = client.post("/firstRouting/post-endpoint").request()) {
            assertThat(response.status(), is(CREATED_201));
        }
        // GET
        try (Http1ClientResponse response = client.get("/firstRouting/get-endpoint").request()) {
            assertThat(response.status(), is(OK_200));
        }
    }

    @Test
    public void routingAsFilter() {
        // POST
        try (Http1ClientResponse response = client.post("/routingAsFilter/post-endpoint").request()) {
            assertThat(response.status(), is(CREATED_201));
        }
        // GET
        try (Http1ClientResponse response = client.get("/routingAsFilter/get-endpoint").request()) {
            assertThat(response.status(), is(OK_200));
        }
    }

    @Test
    public void parametersAndHeaders() {
        try (Http1ClientResponse response = client.get("/parametersAndHeaders/context/aaa")
                .queryParam("bar", "bbb")
                .header(FOO_HEADER, "ccc")
                .request()) {

            assertThat(response.status(), is(OK_200));
            String s = response.entity().as(String.class);
            assertThat(s, containsString("id: aaa"));
            assertThat(s, containsString("bar: bbb"));
            assertThat(s, containsString("foo: ccc"));
        }
    }

    @Test
    public void organiseCode() {
        // List
        try (Http1ClientResponse response = client.get("/organiseCode/catalog-context-path").request()) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("1, 2, 3, 4, 5"));
        }

        // Get by id
        try (Http1ClientResponse response = client.get("/organiseCode/catalog-context-path/aaa").request()) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("Item: aaa"));
        }
    }

    @Test
    public void readContentEntity() {
        // foo
        try (Http1ClientResponse response = client.post("/readContentEntity/foo").submit("aaa")) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("aaa"));
        }

        // bar
        try (Http1ClientResponse response = client.post("/readContentEntity/bar").submit("aaa")) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("aaa"));
        }
    }

    @Test
    public void mediaReader() {
        try (Http1ClientResponse response = client.post("/mediaReader/create-record")
                .contentType(NameSupport.APP_NAME)
                .submit("John Smith")) {
            assertThat(response.status(), is(CREATED_201));
            assertThat(response.entity().as(String.class), is("John Smith"));
        }

        // Unsupported Content-Type
        try (Http1ClientResponse response = client.post("/mediaReader/create-record")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit("John Smith")) {
            assertThat(response.status(), is(INTERNAL_SERVER_ERROR_500));
        }
    }

    @Test
    public void supports() {
        // Static content
        try (Http1ClientResponse response = client.get("/supports/index.html").request()) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.headers().first(Http.HeaderNames.CONTENT_TYPE).orElse(null), is(MediaTypes.TEXT_HTML.text()));
        }

        // JSON
        try (Http1ClientResponse response = client.get("/supports/hello/Europe").request()) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("{\"message\":\"Hello Europe\"}"));
        }
    }

    @Test
    public void errorHandling() {
        // Valid
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit("2")) {
            assertThat(response.status(), is(OK_200));
            assertThat(response.entity().as(String.class), is("100 / 2 = 50"));
        }

        // Zero
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit("0")) {
            assertThat(response.status(), is(PRECONDITION_FAILED_412));
        }

        // NaN
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit("aaa")) {
            assertThat(response.status(), is(BAD_REQUEST_400));
        }
    }
}
