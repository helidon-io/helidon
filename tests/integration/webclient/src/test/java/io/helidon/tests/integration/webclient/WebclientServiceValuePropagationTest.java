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

import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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

    private static final class UriChangingService implements WebClientService {

        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.uri("http://localhost:" + webServer.port() + "/greet");
            request.path("valuesPropagated");
            request.queryParams().add("param", "Hi");
            request.fragment("Test");
            return Single.just(request);
        }
    }

}
