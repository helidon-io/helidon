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
package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletionException;

import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests propagation of the request parts defined in WebClientService.
 */
public class WebclientServiceValuePropagationTest extends TestParent {

    @Test
    public void testUriPartsPropagation() {
        WebClient webClient = WebClient.builder()
                .baseUri("http://invalid")
                .addService(new UriChangingService())
                .build();

        String response = webClient.get()
                .path("replace/me")
                .request(String.class)
                .await();

        assertThat(response, is("Hi Test"));
    }

    @Test
    public void testInvalidSchema() {
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:80")
                .addService(new InvalidSchemaService())
                .build();

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> webClient.get().request(String.class).await());

        assertThat(exception.getCause().getMessage(), is("invalid transport protocol is not supported!"));

    }

    private static final class UriChangingService implements WebClientService {

        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.schema("http");
            request.host("localhost");
            request.port(webServer.port());
            request.path("/greet/valuesPropagated");
            request.queryParams().add("param", "Hi");
            request.fragment("Test");
            return Single.just(request);
        }
    }

    private static final class InvalidSchemaService implements WebClientService {

        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.schema("invalid");
            return Single.just(request);
        }
    }

}
