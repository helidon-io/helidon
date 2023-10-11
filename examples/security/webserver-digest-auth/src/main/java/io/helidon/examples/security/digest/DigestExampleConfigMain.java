/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.security.digest;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.http.HttpMediaTypes;
import io.helidon.logging.common.LogConfig;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

/**
 * Example of HTTP digest authentication with Web Server fully configured in config file.
 */
public final class DigestExampleConfigMain {
    private DigestExampleConfigMain() {
    }

    /**
     * Starts this example. Loads configuration from src/main/resources/application.conf. See standard output for instructions.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %d ms

                Started server on localhost:%2$d

                Users:
                jack/password in roles: user, admin
                jill/password in roles: user
                john/password in no roles

                ***********************
                ** Endpoints:        **
                ***********************

                No authentication: http://localhost:%2$d/public
                No roles required, authenticated: http://localhost:%2$d/noRoles
                User role required: http://localhost:%2$d/user
                Admin role required: http://localhost:%2$d/admin
                Always forbidden (uses role nobody is in), audited: http://localhost:%2$d/deny
                Admin role required, authenticated, authentication optional, audited \
                (always forbidden - challenge is not returned as authentication is optional): http://localhost:%2$d/noAuthn

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS), server.port());
    }

    static void setup(WebServerConfig.Builder server) {
        Config config = Config.create();
        server.config(config.get("server"))
                .routing(routing -> routing
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                    res.send("Hello, you are: \n" + securityContext
                            .map(ctx -> ctx.user().orElse(SecurityContext.ANONYMOUS).toString())
                            .orElse("Security context is null"));
                }));
    }
}
