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

package io.helidon.security.examples.signatures;

import java.util.Optional;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Example of authentication of service with http signatures, using configuration file as much as possible.
 */
public class SignatureExampleConfigMain {

    // used from unit tests
    private static WebServer service1Server;
    private static WebServer service2Server;

    private SignatureExampleConfigMain() {
    }

    /**
     * Starts this example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // to allow us to set host header explicitly
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        // start service 2 first, as it is required by service 1
        service2Server = SignatureExampleUtil.startServer(routing2(), 9080);
        service1Server = SignatureExampleUtil.startServer(routing1(), 8080);

        System.out.println("Signature example: from configuration");
        System.out.println();
        System.out.println("Users:");
        System.out.println("jack/password in roles: user, admin");
        System.out.println("jill/password in roles: user");
        System.out.println("john/password in no roles");
        System.out.println();
        System.out.println("***********************");
        System.out.println("** Endpoints:        **");
        System.out.println("***********************");
        System.out.println("Basic authentication, user role required, will use symmetric signatures for outbound:");
        System.out.printf("  http://localhost:%1$d/service1%n", service1Server.port());
        System.out.println("Basic authentication, user role required, will use asymmetric signatures for outbound:");
        System.out.printf("  http://localhost:%1$d/service1-rsa%n", service1Server.port());
        System.out.println();
    }

    private static Routing routing2() {
        Config config = config("service2.yaml");
        // build routing (security is loaded from config)
        return Routing.builder()
                // helper method to load both security and web server security from configuration
                .register(WebSecurity.create(config.get("security")))
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/{*}", (req, res) -> {
                    Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                    res.headers().contentType(MediaType.TEXT_PLAIN.withCharset("UTF-8"));
                    res.send("Response from service2, you are: \n" + securityContext
                            .flatMap(SecurityContext::user)
                            .map(Subject::toString)
                            .orElse("Security context is null") + ", service: " + securityContext
                            .flatMap(SecurityContext::service)
                            .map(Subject::toString));
                })
                .build();
    }

    private static Routing routing1() {
        Config config = config("service1.yaml");

        // build routing (security is loaded from config)
        return Routing.builder()
                // helper method to load both security and web server security from configuration
                .register(WebSecurity.create(config.get("security")))
                // web server does not (yet) have possibility to configure routes in config files, so explicit...
                .get("/service1", (req, res) -> {
                    SignatureExampleUtil.processService1Request(req, res, "/service2", service2Server.port());
                })
                .get("/service1-rsa", (req, res) -> {
                    SignatureExampleUtil.processService1Request(req, res, "/service2-rsa", service2Server.port());
                })
                .build();
    }

    private static Config config(String confFile) {
        // load configuration
        return Config.builder()
                .sources(ConfigSources.classpath(confFile))
                .build();
    }

    static WebServer getService1Server() {
        return service1Server;
    }

    static WebServer getService2Server() {
        return service2Server;
    }
}
