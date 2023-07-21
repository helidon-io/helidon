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

package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletionException;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriPartTest extends TestParent {

    private static final String EXPECTED_QUERY = "some query &#&@Đ value";

    UriPartTest(WebServer server) {
        super(server);
    }

    @Test
    void testQuerySpace() {
        String response = client.get()
                .path("obtainedQuery")
                .queryParam("param", "test")
                .queryParam("test", EXPECTED_QUERY)
                .requestEntity(String.class);
        assertThat(response.trim(), is(EXPECTED_QUERY));
        try (Http1ClientResponse fullResponse = client.get()
                .path("obtainedQuery")
                .queryParam("test", EXPECTED_QUERY)
                .skipUriEncoding(true)
                .request()) {
            assertThat(fullResponse.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    @Test
    void testQueryKeySpace() {
        String queryNameWithSpace = "query name with space";
        String response = client.get()
                .path("obtainedQuery")
                .queryParam("param", queryNameWithSpace)
                .queryParam(queryNameWithSpace, EXPECTED_QUERY)
                .requestEntity(String.class);
        assertThat(response.trim(), is(EXPECTED_QUERY));
        assertThrows(IllegalArgumentException.class, () -> client.get()
                .path("obtainedQuery")
                .queryParam("param", queryNameWithSpace)
                .skipUriEncoding(true)
                .request(String.class));
    }

    @Test
    void testPathWithSpace() {
        String response = client.get()
                .path("pattern with space")
                .requestEntity(String.class);
        assertThat(response.trim(), is("{\"message\":\"Hello World!\"}"));
        assertThrows(IllegalArgumentException.class, () -> client.get()
                .path("pattern with space")
                .skipUriEncoding(true)
                .request(String.class));
    }

    @Test
    void testFragment() {
        String fragment = "super fragment#&?/";
        String response = client.get()
                .path("obtainedQuery")
                .queryParam("param", "empty")
                .queryParam("empty", "")
                .fragment(fragment)
                .requestEntity(String.class);
        assertThat(response.trim(), is(fragment));
    }

    @Test
    void testBadFragment() {
        String fragment = "super fragment#&?/"; // contains illegal characters, that should break validation
        try (HttpClientResponse response = client.get()
                .skipUriEncoding(true)
                .fragment(fragment)
                .request()) {

            assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    @Test
    void testQueryNotDecoded() {
        Http1Client webClient = createNewClient((chain, request) -> {
            assertThat(request.uri().query().value(), is("first&second%26=val&ue%26"));
            return chain.proceed(request);
        });
        String response = webClient.get()
                .queryParam("first&second%26", "val&ue%26")
                .skipUriEncoding(true)
                .requestEntity(String.class);
        assertThat(response.trim(), is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    void testQueryNotDoubleEncoded() {
        Http1Client webClient = createNewClient((chain, request) -> {
            assertThat(request.uri().query().rawValue(), is("first%26second%26=val%26ue%26"));
            return chain.proceed(request);
        });
        String response = webClient.get()
                .queryParam("first&second%26", "val&ue%26")
                .skipUriEncoding(true)
                .requestEntity(String.class);
        assertThat(response.trim(), is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    void testPathNotDecoded() {
        Http1Client webClient = createNewClient((chain, request) -> {
            assertThat(request.uri().path().rawPath(), is("/greet/path%26"));
            return chain.proceed(request);
        });
        assertThrows(CompletionException.class, () -> webClient.get()
                .path("path%26")
                .skipUriEncoding(true)
                .request(String.class));
    }

}
