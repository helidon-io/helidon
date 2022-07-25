/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webserver.context.propagation;

import java.time.Duration;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.context.propagation.ContextPropagationFilter;

/**
 * Main test class.
 * Starts the server and configures context propagation on it.
 */
public class ContextPropagationMain {
    static final String HEADER_VALUE = "x_helidon_value";
    static final String HEADER_VALUES = "x_helidon_values";
    static final String HEADER_DEFAULT = "x_helidon_default";
    static final String HEADER_DEFAULTS = "x_helidon_defaults";
    static final String CLASSIFIER_VALUE = "io.helidon.tests.integration.value";
    static final String CLASSIFIER_VALUES = "io.helidon.tests.integration.values";
    static final String CLASSIFIER_DEFAULT = "io.helidon.tests.integration.default";
    static final String CLASSIFIER_DEFAULTS = "io.helidon.tests.integration.defaults";
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static WebServer server;
    private static WebClient client;

    private ContextPropagationMain() {
    }

    /**
     * Main method.
     *
     * @param args command line arguments are ignored
     */
    public static void main(String[] args) {
        Config config = Config.create();

        server = WebServer.builder()
                .config(config.get("server"))
                .addMediaSupport(JsonbSupport.create())
                .routing(Routing.builder()
                                 .any(ContextPropagationFilter.create(config.get("server.context")))
                                 .get("/", ContextPropagationMain::route)
                                 .build())
                .build()
                .start()
                .await(TIMEOUT);

        client = WebClient.builder()
                .config(config.get("client"))
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    static WebServer server() {
        return server;
    }

    static WebClient client() {
        return client;
    }

    private static void route(ServerRequest req, ServerResponse res) {
        Context ctx = req.context();
        DataDto dto = new DataDto();

        ctx.get(CLASSIFIER_VALUE, String.class).ifPresent(dto::setValue);
        ctx.get(CLASSIFIER_VALUES, String[].class).ifPresent(dto::setValues);

        ctx.get(CLASSIFIER_DEFAULT, String.class).ifPresent(dto::setDefaultValue);
        ctx.get(CLASSIFIER_DEFAULTS, String[].class).ifPresent(dto::setDefaultValues);

        res.send(dto);
    }
}
