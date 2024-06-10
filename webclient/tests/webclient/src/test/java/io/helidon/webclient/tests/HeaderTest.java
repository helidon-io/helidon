/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Tests to test headers.
 */
public class HeaderTest extends TestParent {

    private static final String TEST_USER = "unit-test-user";

    HeaderTest(WebServer server) {
        super(server);
    }

    @Test
    public void userAgentNotOverridden() {
        Http1Client webClient = createNewClient(new HeaderTestService(TEST_USER));

        webClient.get()
                .header(HeaderNames.USER_AGENT, TEST_USER)
                .request(JsonObject.class);
    }

    @Test
    public void contentLengthSet() {
        Http1Client webClient = createNewClient();

        try (Http1ClientResponse res = webClient.post("contentLength").request()) {
            String contentLength = res.as(String.class);
            assertThat(contentLength, is(HeaderNames.CONTENT_LENGTH + " is 0"));
        }

        try (Http1ClientResponse res = webClient.post("contentLength").request()) {
            String contentLength = res.as(String.class);
            assertThat(contentLength, is(HeaderNames.CONTENT_LENGTH + " is 0"));
        }

        String sampleSmallEntity = "Hi there";
        try (Http1ClientResponse res = webClient.post("contentLength").submit(sampleSmallEntity)) {
            String contentLength = res.as(String.class);
            assertThat(contentLength, is(HeaderNames.CONTENT_LENGTH + " is " + sampleSmallEntity.length()));
        }

        try (Http1ClientResponse res = webClient.post()
                .headers(headers -> {
                    headers.contentLength(0);
                })
                .path("contentLength")
                .submit(sampleSmallEntity)) {
            String contentLength = res.as(String.class);
            assertThat(contentLength, is(HeaderNames.CONTENT_LENGTH + " is " + sampleSmallEntity.length()));
        }
    }

    @Test
    public void testHeaderDuplication() {
        WebClient webClient = WebClient.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://localhost:" + server.port() + "/greet/header")
                .addHeader("someCustomHeader", "header value")
                .build();

        String response = webClient.get().requestEntity(String.class);
        assertThat(response, is("\"someCustomHeader\" header was not duplicated!"));
    }

    private record HeaderTestService(String user) implements WebClientService {

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            List<String> userAgent = request.headers().all(HeaderNames.USER_AGENT, List::of);
            assertThat(userAgent, hasSize(1));
            assertThat(userAgent, contains(user));
            return chain.proceed(request);
        }
    }
}
