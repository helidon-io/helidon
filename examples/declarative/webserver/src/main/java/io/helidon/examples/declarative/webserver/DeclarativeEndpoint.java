/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.declarative.webserver;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Http;
import io.helidon.http.NotFoundException;
import io.helidon.service.inject.api.Injection;
import io.helidon.webserver.http.ServerResponse;

@Injection.Singleton
@Http.Path("/declarative")
class DeclarativeEndpoint {
    private static final Header CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=UTF-8");
    private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, "13");
    private static final Header SERVER = HeaderValues.createCached(HeaderNames.SERVER, "Helidon");
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @Http.Path("/plaintext")
    @Http.GET
    byte[] plaintext(ServerResponse res) {
        res.header(CONTENT_LENGTH);
        res.header(CONTENT_TYPE);
        res.header(SERVER);
        return RESPONSE_BYTES;
    }

    @Http.Path("/greet/{name}")
    @Http.GET
    String params(@Http.PathParam("name") String name,
                  @Http.QueryParam(value = "throw") Optional<Boolean> shouldThrow,
                  @Http.HeaderParam(HeaderNames.HOST_STRING) String hostHeader) {
        if (shouldThrow.orElse(false)) {
            throw new NotFoundException("Not found");
        }
        return "Hello " + name + ", host header: " + hostHeader;
    }
}
