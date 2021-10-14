/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.idcs;

import java.util.Optional;

import io.helidon.common.LogConfig;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.oidc.OidcSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * IDCS Login example main class using configuration .
 */
public final class IdcsMain {
    private static volatile WebServer theServer;

    private IdcsMain() {
    }

    public static WebServer getTheServer() {
        return theServer;
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // load logging configuration
        LogConfig.configureRuntime();

        Config config = buildConfig();

        Security security = Security.create(config.get("security"));
        // this is needed for proper encryption/decryption of cookies
        Contexts.globalContext().register(security);

        Routing.Builder routing = Routing.builder()
                .register(WebSecurity.create(security, config.get("security")))
                // IDCS requires a web resource for redirects
                .register(OidcSupport.create(config))
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/rest/profile", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Response from config based service, you are: \n" + securityContext
                            .flatMap(SecurityContext::user)
                            .map(Subject::toString)
                            .orElse("Security context is null"));
                })
                .get("/loggedout", (req, res) -> res.send("You have been logged out"));

        theServer = WebServer.create(routing, config.get("server"));

        IdcsUtil.start(theServer);
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
