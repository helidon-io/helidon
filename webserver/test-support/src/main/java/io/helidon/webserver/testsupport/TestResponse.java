/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.webserver.WebServer;

/**
 * Represents a response suitable for testing asserts. Cache all data in memory.
 */
public class TestResponse {

    private final Http.ResponseStatus status;
    private final Parameters headers;
    // todo Needs much better solution.
    private final TestClient.TestBareResponse bareResponse;

    /**
     * Creates new instance.
     *
     * @param status an HTTP status
     * @param headers HTTP headers
     * @param bareResponse the test bare response
     */
    TestResponse(Http.ResponseStatus status,
                 Map<String, List<String>> headers,
                 TestClient.TestBareResponse bareResponse) {
        this.status = status;
        this.headers = new ReadOnlyParameters(headers);
        this.bareResponse = bareResponse;
    }

    /**
     * Returns an HTTP status code of the response.
     *
     * @return an HTTP status code.
     */
    public Http.ResponseStatus status() {
        return status;
    }

    /**
     * Returns all response headers.
     *
     * @return response headers.
     */
    public Parameters headers() {
        return headers;
    }

    /**
     * Returns content as bytes when response is completed.
     *
     * @return a completion stage of body bytes.
     */
    public CompletableFuture<byte[]> asBytes() {
        return bareResponse.whenCompleted()
                           .thenApply(br -> bareResponse.asBytes())
                           .toCompletableFuture();
    }

    /**
     * Returns content as {@link String} when response is completed when coding charset is get from response and defaults to
     * {@code UTF-8}.
     *
     * @return a completion stage of response.
     */
    public CompletableFuture<String> asString() {
        Charset charset = headers.first(Http.Header.CONTENT_TYPE)
                                 .map(MediaType::parse)
                .flatMap(MediaType::charset)
                                 .map(s -> {
                                     try {
                                         return Charset.forName(s);
                                     } catch (Exception e) {
                                         return null;
                                     }
                                 })
                                 .orElse(StandardCharsets.UTF_8);
        return asBytes().thenApply(bts -> new String(bts, charset));
    }

    /**
     * Returns a web server instance.
     *
     * @return a web server associated with this test call.
     */
    public WebServer webServer() {
        return bareResponse.webServer();
    }
}
