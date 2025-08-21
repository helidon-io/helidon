/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HeadersWithErrorHandlerTest {
    private static final Header CORS_ALL_ORIGINS = HeaderValues.create(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    private static final Header TEST_ORIGIN = HeaderValues.create(HeaderNames.ORIGIN, "http://test.origin");

    private final WebClient webClient;

    HeadersWithErrorHandlerTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.register(CorsSupport.create(), new TestHttpService())
                .error(RuntimeException.class, new TestErrorHandler());
    }

    @Test
    void checkCorsOnSuccess() {
        var response = webClient.get("/ok")
                .header(TEST_ORIGIN)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("hello"));
        assertThat(response.headers(), HttpHeaderMatcher.hasHeader(CORS_ALL_ORIGINS));
    }

    @Test
    void checkCorsOnError() {
        var response = webClient.get("/error")
                .header(TEST_ORIGIN)
                .request(String.class);

        assertThat(response.status(), is(Status.BAD_REQUEST_400));
        assertThat(response.entity(), is("Failed, but error handled"));
        assertThat(response.headers(), HttpHeaderMatcher.hasHeader(CORS_ALL_ORIGINS));
    }

    private static class TestHttpService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
            rules.get("/ok", (req, res) -> res.send("hello"))
                    .get("/error", (req, res) -> {
                        throw new RuntimeException("Unit test exception");
                    });
        }
    }

    private static class TestErrorHandler implements ErrorHandler<RuntimeException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, RuntimeException throwable) {
            res.status(Status.BAD_REQUEST_400)
                    .send("Failed, but error handled");
        }
    }
}
