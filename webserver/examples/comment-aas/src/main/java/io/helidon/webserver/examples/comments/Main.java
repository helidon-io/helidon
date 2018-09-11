/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import io.helidon.common.OptionalHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.etcd.EtcdConfigSourceBuilder;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
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
        Config config = loadConfig();

        boolean acceptAnonymousUsers = config.get("anonymous-enabled").asBoolean(false);
        ServerConfiguration serverConfig = config.get("webserver").as(ServerConfiguration.class);

        WebServer server = WebServer.create(serverConfig, createRouting(acceptAnonymousUsers));

        // Start the server and print some info.
        server.start().thenAccept(Main::printStartupMessage);

        // Server uses non-demon threads. It is not needed to block a main thread. Just react!
        server.whenShutdown()
                .thenRun(() -> System.out.println("Comments-As-A-Service is DOWN. Good bye!"))
                .thenRun(() -> System.exit(0));
    }

    private static Config loadConfig() {
        String etcdUri = Optional.ofNullable(System.getenv("ETCD_URI"))
                .orElse("http://localhost:2379");

        return Config.builder()
                     .sources(EtcdConfigSourceBuilder.from(URI.create(etcdUri),
                                                           "comments-aas-config",
                                                           EtcdConfigSourceBuilder.EtcdApi.v2)
                                                     .mediaType("application/x-yaml")
                                                     .optional()
                                                     .build(),
                              ConfigSources.classpath("application.conf"))
                     .build();
    }

    static Routing createRouting(boolean acceptAnonymousUsers) {
        return Routing.builder()
                // Filter that translates user identity header into the contextual "user" information
                .any((req, res) -> {

                    String user = OptionalHelper.from(req.headers().first("user-identity"))
                            .or(() -> acceptAnonymousUsers ? Optional.of("anonymous") : Optional.empty())
                            .asOptional()
                            .orElseThrow(() -> new HttpException("Anonymous access is forbidden!", Http.Status.FORBIDDEN_403));

                    req.context().register("user", user);
                    req.next();
                })


                // Main service logic part is registered as a separated class to "/comments" context root
                .register("/comments", new CommentsService())


                // Shut down logic is registered to "/mgmt/shutdown" path
                .post("/mgmt/shutdown", (req, res) -> {
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Shutting down 'COMMENTS-As-A-Service' server. Good bye!\n");
                    // Use reactive API nature to stop the server AFTER the response was sent.
                    res.whenSent().thenRun(() -> req.webServer().shutdown());
                })


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

    private static void printStartupMessage(WebServer ws) {
        String urlBase = "http://localhost:" + ws.port();
        StringBuilder info = new StringBuilder();
        info.append("Comments-As-A-Service for your service! ").append(urlBase).append('\n');
        info.append("  - ").append("Add comment: POST ").append(urlBase).append("/comments/{topic}\n");
        info.append("  - ").append("List comments: GET ").append(urlBase).append("/comments/{topic}\n\n");
        info.append("Shutdown: POST ").append(urlBase).append("/mgmt/shutdown\n\n");
        System.out.println(info);
    }
}
