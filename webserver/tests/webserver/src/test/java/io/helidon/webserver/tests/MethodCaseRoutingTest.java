/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.http.Status;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.Socket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MethodCaseRoutingTest {
    private final HttpClient client;
    private final URI compatibleUri;
    private final URI caseSensitiveUri;

    MethodCaseRoutingTest(@Socket("@default") URI compatibleUri,
                          @Socket("case-sensitive") URI caseSensitiveUri) {
        this.compatibleUri = compatibleUri;
        this.caseSensitiveUri = caseSensitiveUri;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void compatibleRouting(HttpRouting.Builder routing) {
        routing(routing);
    }

    @SetUpRoute("case-sensitive")
    static void caseSensitiveRouting(ListenerConfig.Builder listener, HttpRouting.Builder routing) {
        listener.caseSensitiveMethods(true);
        routing(routing);
    }

    @Test
    void defaultListenerNormalizesLowercaseAndUppercaseMethods() throws IOException, InterruptedException {
        assertResponse(compatibleUri, "delete", "/delete", Status.OK_200, "DELETE");
        assertResponse(compatibleUri, "DELETE", "/delete", Status.OK_200, "DELETE");
    }

    @Test
    void caseSensitiveListenerDistinguishesLowercaseAndUppercaseMethods() throws IOException, InterruptedException {
        assertResponse(caseSensitiveUri, "delete", "/delete", Status.NOT_FOUND_404, "");
        assertResponse(caseSensitiveUri, "DELETE", "/delete", Status.OK_200, "DELETE");
    }

    @Test
    void caseSensitiveListenerExposesExactMethodText() throws IOException, InterruptedException {
        assertResponse(caseSensitiveUri, "delete", "/method", Status.OK_200, "delete");
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.delete("/delete", (req, res) -> res.send(req.prologue().method().text()))
                .any("/method", (req, res) -> res.send(req.prologue().method().text()));
    }

    private void assertResponse(URI uri,
                                String method,
                                String path,
                                Status expectedStatus,
                                String expectedBody) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve(path))
                                                            .method(method, HttpRequest.BodyPublishers.noBody())
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(expectedStatus.code()));
        if (!expectedBody.isEmpty()) {
            assertThat(response.body(), is(expectedBody));
        }
        assertThat(response.version(), is(HttpClient.Version.HTTP_1_1));
    }
}
