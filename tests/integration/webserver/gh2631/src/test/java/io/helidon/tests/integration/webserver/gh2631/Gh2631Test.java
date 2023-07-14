/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webserver.gh2631;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Gh2631Test {
    private final Http1Client client;

    Gh2631Test(WebServer server) {
        this.client = Http1Client.builder()
                               .baseUri("http://localhost:" + server.port())
                               .followRedirect(true)
                               .build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder builder) {
        builder.routing(Gh2631::routing);
    }

    @Test
    void testClasspathNoFallback() {
        String value = get("/simple/first/");
        assertThat(value, is("first"));

        value = get("/simple/first/index.txt");
        assertThat(value, is("first"));
    }

    @Test
    void testClasspathNoFallbackMissing() {
        Http1ClientResponse response = getResponse("/simple/second/");
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    void testClasspathFallback() {
        String value = get("/fallback/first/");
        assertThat(value, is("first"));

        value = get("/fallback/first/index.txt");
        assertThat(value, is("first"));
    }

    @Test
    void testClasspathFallbackMissing() {
        String value = get("/fallback/second/");
        assertThat(value, is("fallback"));

        value = get("/fallback/second/any/path/anyFile.txt");
        assertThat(value, is("fallback"));
    }

    @Test
    void testFileNoFallback() {
        String value = get("/simpleFile/first/");
        assertThat(value, is("first"));

        value = get("/simpleFile/first/index.txt");
        assertThat(value, is("first"));
    }

    @Test
    void testFileNoFallbackMissing() {
        Http1ClientResponse response = getResponse("/simpleFile/second/");
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    void testFileFallback() {
        String value = get("/fallbackFile/first/");
        assertThat(value, is("first"));

        value = get("/fallbackFile/first/index.txt");
        assertThat(value, is("first"));
    }

    @Test
    void testFileFallbackMissing() {
        String value = get("/fallbackFile/second/");
        assertThat(value, is("fallback"));

        value = get("/fallbackFile/second/any/path/anyFile.txt");
        assertThat(value, is("fallback"));
    }

    private Http1ClientResponse getResponse(String path) {
        return client.get()
                .path(path)
                .request();
    }

    private String get(String path) {
        return client.get()
                .path(path)
                .request(String.class);
    }
}