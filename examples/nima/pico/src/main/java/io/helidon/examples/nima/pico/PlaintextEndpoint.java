/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.pico;

import java.nio.charset.StandardCharsets;

import io.helidon.common.http.GET;
import io.helidon.common.http.Http;
import io.helidon.common.http.Path;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.inject.Singleton;

@Singleton
@Path("/plaintext")
class PlaintextEndpoint {
    static final Http.HeaderValue CONTENT_TYPE = Http.Header.createCached(Http.Header.CONTENT_TYPE,
                                                                          "text/plain; charset=UTF-8");
    static final Http.HeaderValue CONTENT_LENGTH = Http.Header.createCached(Http.Header.CONTENT_LENGTH, "13");
    static final Http.HeaderValue SERVER = Http.Header.createCached(Http.Header.SERVER, "Nima");

    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @GET
    void plaintext(ServerResponse res) {
        res.header(CONTENT_LENGTH);
        res.header(CONTENT_TYPE);
        res.header(SERVER);
        res.send(RESPONSE_BYTES);
    }
}
