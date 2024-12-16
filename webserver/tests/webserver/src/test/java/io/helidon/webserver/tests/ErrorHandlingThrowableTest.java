/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class ErrorHandlingThrowableTest {
    private static final HeaderName CONTROL_HEADER = HeaderNames.create("X-HELIDON-JUNIT");
    private static final Header THROW = HeaderValues.create(CONTROL_HEADER, "throw");

    private final Http1Client client;

    ErrorHandlingThrowableTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.error(SomeError.class, new SomeErrorHandler())
                .get("/", ErrorHandlingThrowableTest::handler);
    }

    @Test
    void testOk() {
        String response = client.get()
                .requestEntity(String.class);
        assertThat(response, is("Done"));
    }

    @Test
    void testSomeError() {
        ClientResponseTyped<String> response = client.get()
                .header(THROW)
                .request(String.class);
        assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        assertThat(response.entity(), is("Handled"));
    }

    private static void handler(ServerRequest req, ServerResponse res) throws Exception {
        if (req.headers().contains(THROW)) {
            throw new SomeError();
        }
        res.send("Done");
    }

    private static class SomeErrorHandler implements ErrorHandler<SomeError> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, SomeError throwable) {
            res.header(HeaderValues.CONNECTION_CLOSE);
            res.send("Handled");
        }
    }

    private static class SomeError extends Error {
    }
}
