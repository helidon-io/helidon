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

package io.helidon.webserver.examples.basics;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class MainTest {

    private static final Http.HeaderName FOO_HEADER = Http.Header.create("foo");

    private final Http1Client client;

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Main.firstRouting(server);
        Main.mediaReader(server);
        Main.advancedRouting(server);
        Main.organiseCode(server);
        Main.routingAsFilter(server);
        Main.parametersAndHeaders(server);
        Main.errorHandling(server);
        Main.readContentEntity(server);
        Main.supports(server);
    }

    @Test
    public void firstRouting() {
        // POST
        try (Http1ClientResponse response = client.post("/firstRouting/post-endpoint").request()) {
            assertThat(response.status().code(), is(201));
        }
        // GET
        try (Http1ClientResponse response = client.get("/firstRouting/get-endpoint").request()) {
            assertThat(response.status().code(), is(204));
            assertThat(response.entity().as(String.class), is("Hello World!"));
        }
    }

    @Test
    public void routingAsFilter() {
        // POST
        try (Http1ClientResponse response = client.post("/routingAsFilter/post-endpoint").request()) {
            assertThat(response.status().code(), is(201));
        }
        // GET
        try (Http1ClientResponse response = client.get("/routingAsFilter/get-endpoint").request()) {
            assertThat(response.status().code(), is(204));
        }
    }

    @Test
    public void parametersAndHeaders() {
        try (Http1ClientResponse response = client.get("/parametersAndHeaders/context/aaa")
                .queryParam("bar", "bbb")
                .header(FOO_HEADER, "ccc")
                .request()) {

            assertThat(response.status().code(), is(200));
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
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("1, 2, 3, 4, 5"));
        }

        // Get by id
        try (Http1ClientResponse response = client.get("/organiseCode/catalog-context-path/aaa").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("Item: aaa"));
        }
    }

    @Test
    public void readContentEntity() {
        // foo
        try (Http1ClientResponse response = client.post("/readContentEntity/foo").submit("aaa")) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("aaa"));
        }

        // bar
        try (Http1ClientResponse response = client.post("/readContentEntity/bar").submit("aaa")) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("aaa"));
        }
    }

    @Test
    public void mediaReader() {
        try (Http1ClientResponse response = client.post("/mediaReader/create-record")
                .contentType(NameSupport.APP_NAME)
                .submit("John Smith")) {
            assertThat(response.status().code(), is(201));
            assertThat(response.entity().as(String.class), is("John Smith"));
        }

        // Unsupported Content-Type
        try (Http1ClientResponse response = client.post("/mediaReader/create-record")
                .contentType(HttpMediaType.TEXT_PLAIN)
                .submit("John Smith")) {
            assertThat(response.status().code(), is(500));
        }
    }

    @Test
    public void supports() {
        // Jersey
        try (Http1ClientResponse response = client.get("/supports/api/hw").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("Hello world!"));
        }

        // Static content
        try (Http1ClientResponse response = client.get("/supports/index.html").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaTypes.TEXT_HTML.text()));
        }

        // JSON
        try (Http1ClientResponse response = client.get("/supports/hello/Europe").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("{\"message\":\"Hello Europe\"}"));
        }
    }

    @Test
    public void errorHandling() {
        // Valid
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(HttpMediaType.TEXT_PLAIN)
                .submit("2")) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().as(String.class), is("100 / 2 = 50"));
        }

        // Zero
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(HttpMediaType.TEXT_PLAIN)
                .submit("0")) {
            assertThat(response.status().code(), is(412));
        }

        // NaN
        try (Http1ClientResponse response = client.post("/errorHandling/compute")
                .contentType(HttpMediaType.TEXT_PLAIN)
                .submit("aaa")) {
            assertThat(response.status().code(), is(400));
        }
    }
}
