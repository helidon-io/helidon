/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.google;

import java.util.Optional;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Google login button example main class using configuration.
 */
public final class GoogleConfigMain {
    private static volatile WebServer theServer;

    private GoogleConfigMain() {
    }

    public static WebServer getTheServer() {
        return theServer;
    }

    /**
     * Start the example.
     *
     * @param args ignored
     * @throws InterruptedException if server startup is interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        Config config = buildConfig();

        Routing.Builder routing = Routing.builder()
                // helper method to load both security and web server security from configuration
                .register(WebSecurity.from(config))
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/rest/profile", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Response from config based service, you are: \n" + securityContext
                            .flatMap(SecurityContext::getUser)
                            .map(Subject::toString)
                            .orElse("Security context is null"));
                })
                .register(StaticContentSupport.create("/WEB"));

        theServer = GoogleUtil.startIt(routing);
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        // you can use this file to override the defaults built-in
                        file(System.getProperty("user.home") + "/helidon/conf/examples.yaml").optional(),
                        // in jar file (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                .build();
    }
}
