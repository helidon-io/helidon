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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests to test headers.
 */
public class HeaderTest extends TestParent {

    private static final String TEST_USER = "unit-test-user";

    @Test
    public void userAgentNotOverridden() throws Exception {
        WebClient webClient = createNewClient(new HeaderTestService(TEST_USER));

        webClient.get()
                .headers(headers -> {
                    headers.add(Http.Header.USER_AGENT, TEST_USER);
                    return headers;
                })
                .request(JsonObject.class)
                .toCompletableFuture()
                .get();
    }

    private static final class HeaderTestService implements WebClientService {

        private final String user;

        private HeaderTestService(String user) {
            this.user = user;
        }

        @Override
        public CompletionStage<WebClientServiceRequest> request(WebClientServiceRequest request) {
            return CompletableFuture.completedFuture(request);
        }

        @Override
        public CompletionStage<WebClientServiceResponse> response(WebClientRequestBuilder.ClientRequest request,
                                                                  WebClientServiceResponse response) {
            List<String> userAgent = request.headers().all(Http.Header.USER_AGENT);
            assertThat(userAgent, hasSize(1));
            assertThat(userAgent, contains(user));
            return CompletableFuture.completedFuture(response);
        }
    }

}
