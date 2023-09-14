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

package io.helidon.webclient.tests;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriPartTest extends TestParent {

    private static final String EXPECTED_QUERY = "some query &#&@Đ value";
    private final WebClient noSecurityClient;

    UriPartTest(WebServer server) {
        super(server);
        this.noSecurityClient = noServiceClient();
    }

    @Test
    void testQuerySpace() {
        String response = noSecurityClient.get()
                .path("obtainedQuery")
                .queryParam("param", "test")
                .queryParam("test", EXPECTED_QUERY)
                .requestEntity(String.class);
        assertThat(response.trim(), is(EXPECTED_QUERY));
        try (HttpClientResponse fullResponse = noSecurityClient.get()
                .path("obtainedQuery")
                .queryParam("test", EXPECTED_QUERY)
                .skipUriEncoding(true)
                .request()) {
            assertThat(fullResponse.status(), is(Status.BAD_REQUEST_400));
        }
    }

    @Test
    void testQueryKeySpace() {
        String queryNameWithSpace = "query name with space";
        String response = noSecurityClient.get()
                .path("obtainedQuery")
                .queryParam("param", queryNameWithSpace)
                .queryParam(queryNameWithSpace, EXPECTED_QUERY)
                .requestEntity(String.class);
        assertThat(response.trim(), is(EXPECTED_QUERY));
        assertThrows(IllegalArgumentException.class, () -> noSecurityClient.get()
                .path("obtainedQuery")
                .queryParam("param", queryNameWithSpace)
                .skipUriEncoding(true)
                .requestEntity(String.class));
    }

    @Test
    void testPathWithSpace() {
        String response = noSecurityClient.get()
                .path("pattern with space")
                .requestEntity(String.class);
        assertThat(response.trim(), is("{\"message\":\"Hello World!\"}"));
        assertThrows(IllegalArgumentException.class, () -> noSecurityClient.get()
                .path("pattern with space")
                .skipUriEncoding(true)
                .requestEntity(String.class));
    }

    @Test
    void testFragment() {
        String fragment = "super fragment#&?/";
        String response = noSecurityClient.get()
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
        try (HttpClientResponse response = noSecurityClient.get()
                .skipUriEncoding(true)
                .fragment(fragment)
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
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
            // this is the value we have provided, it must be given back
            assertThat(request.uri().query().value(), is("first&second%26=val&ue%26"));
            // what goes over the network is driven by the skipUriEncoding parameter, and must not be encoded
            assertThat(request.uri().pathWithQueryAndFragment(), is("/greet?first&second%26=val&ue%26"));
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
            assertThat(request.uri().path().path(), is("/greet/path%26"));
            return chain.proceed(request);
        });

        ClientResponseTyped<String> response = webClient.get()
                .skipUriEncoding(true)
                .path("path%26")
                .request(String.class);

        // the path is not valid
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

}
