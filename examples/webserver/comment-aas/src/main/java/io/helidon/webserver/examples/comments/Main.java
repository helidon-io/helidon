/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Status;
import io.helidon.common.http.HttpException;
import io.helidon.config.Config;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * Application java main class.
 * <p>
 * <p>The COMMENTS-As-a-Service application example demonstrates Web Server in its integration role.
 * It integrates various components including <i>Configuration</i> and <i>Security</i>.
 * <p>
 * <p>This WEB application provides possibility to store and read comment related to various topics.
 */
public final class Main {

    static final Http.HeaderName USER_IDENTITY_HEADER = Http.HeaderNames.create("user-identity");

    private Main() {
    }

    /**
     * A java main class.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build().start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/comments");
    }

    static void routing(HttpRouting.Builder routing, boolean acceptAnonymousUsers) {
        // Filter that translates user identity header into the contextual "user" information
        routing.any((req, res) -> {
                   String user = req.headers()
                           .first(USER_IDENTITY_HEADER)
                           .or(() -> acceptAnonymousUsers ? Optional.of("anonymous") : Optional.empty())
                           .orElseThrow(() -> new HttpException("Anonymous access is forbidden!", Status.FORBIDDEN_403));

                   req.context().register("user", user);
                   res.next();
               })
               // Main service logic part is registered as a separated class to "/comments" context root
               .register("/comments", new CommentsService())
               // Error handling for argot expressions.
               .error(ProfanityException.class, (req, res, ex) -> {
                   res.status(Status.NOT_ACCEPTABLE_406);
                   res.send("Expressions like '" + ex.getObfuscatedProfanity() + "' are unacceptable!");
               })
               .error(HttpException.class, (req, res, ex) -> {
                   if (ex.status() == Status.FORBIDDEN_403) {
                       res.status(ex.status());
                       res.send(ex.getMessage());
                   } else {
                       res.next();
                   }
               });
    }

    static void setup(WebServerConfig.Builder server) {
        // Load configuration
        Config config = Config.create();

        server.config(config)
               .routing(r -> routing(r, config.get("anonymous-enabled").asBoolean().orElse(false)));
    }
}
