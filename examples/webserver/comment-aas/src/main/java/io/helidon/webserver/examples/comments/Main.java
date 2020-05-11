/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.comments;

import java.util.Optional;
import java.util.concurrent.CompletionException;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Application java main class.
 * <p>
 * <p>The COMMENTS-As-a-Service application example demonstrates Web Server in its integration role.
 * It integrates various components including <i>Configuration</i> and <i>Security</i>.
 * <p>
 * <p>This WEB application provides possibility to store and read comment related to various topics.
 */
public final class Main {

    private Main() {
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        // Load configuration
        Config config = Config.create();

        boolean acceptAnonymousUsers = config.get("anonymous-enabled").asBoolean().orElse(false);

        WebServer server = WebServer.create(createRouting(acceptAnonymousUsers),
                                            config.get("webserver"));

        // Start the server and print some info.
        server.start().thenAccept((ws) -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port() + "/comments");
        });

        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
    }

    static Routing createRouting(boolean acceptAnonymousUsers) {
        return Routing.builder()
                // Filter that translates user identity header into the contextual "user" information
                .any((req, res) -> {
                    String user = req.headers().first("user-identity")
                            .or(() -> acceptAnonymousUsers ? Optional.of("anonymous") : Optional.empty())
                            .orElseThrow(() -> new HttpException("Anonymous access is forbidden!", Http.Status.FORBIDDEN_403));

                    req.context().register("user", user);
                    req.next();
                })
                // Main service logic part is registered as a separated class to "/comments" context root
                .register("/comments", new CommentsService())
                // Error handling for argot expressions.
                .error(CompletionException.class, (req, res, ex) -> req.next(ex.getCause()))
                .error(ProfanityException.class, (req, res, ex) -> {
                    res.status(Http.Status.NOT_ACCEPTABLE_406);
                    res.send("Expressions like '" + ex.getObfuscatedProfanity() + "' are unacceptable!");
                })
                .error(HttpException.class, (req, res, ex) -> {
                    if (ex.status() == Http.Status.FORBIDDEN_403) {
                        res.status(ex.status());
                        res.send(ex.getMessage());
                    } else {
                        req.next();
                    }
                })
                .build();
    }
}
