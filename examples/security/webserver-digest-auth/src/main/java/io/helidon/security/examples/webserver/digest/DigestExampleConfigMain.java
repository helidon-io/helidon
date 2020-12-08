/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.examples.webserver.digest;

import java.util.Optional;

import io.helidon.common.LogConfig;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Example of HTTP digest authentication with RX Web Server fully configured in config file.
 */
public final class DigestExampleConfigMain {
    // used from unit tests
    private static WebServer server;

    private DigestExampleConfigMain() {
    }

    /**
     * Starts this example. Loads configuration from src/main/resources/application.conf. See standard output for instructions.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        // load configuration
        Config config = Config.create();

        // build routing (security is loaded from config)
        Routing routing = Routing.builder()
                // helper method to load both security and web server security from configuration
                .register(WebSecurity.create(config.get("security")))
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                })
                .build();

        server = DigestExampleUtil.startServer(routing);
    }

    static WebServer getServer() {
        return server;
    }
}
