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

package io.helidon.examples.declarative.webserver;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.NotFoundException;
import io.helidon.service.inject.api.Injection;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@Injection.Singleton
public class ImperativeFeature implements HttpFeature {
    private static final Header CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=UTF-8");
    private static final Header CONTENT_LENGTH = HeaderValues.createCached(HeaderNames.CONTENT_LENGTH, "13");
    private static final Header SERVER = HeaderValues.createCached(HeaderNames.SERVER, "Helidon");
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.register("/imperative", this::routing);
    }

    private void routing(HttpRules rules) {
        rules.get("/plaintext", this::plaintext)
                .get("/greet/{name}", this::greetNamed);
    }

    private void plaintext(ServerRequest req, ServerResponse res) {
        res.header(CONTENT_LENGTH);
        res.header(CONTENT_TYPE);
        res.header(SERVER);
        res.send(RESPONSE_BYTES);
    }

    private void greetNamed(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        Optional<Boolean> shouldThrow = req.query().first("throw").asBoolean().asOptional();
        String hostHeader = req.headers().get(HeaderNames.HOST).getString();

        if (shouldThrow.orElse(false)) {
            throw new NotFoundException("Not found");
        }
        res.send("Hello " + name + ", host header: " + hostHeader);
    }

}
