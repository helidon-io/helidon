/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.security.signatures;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityHttpFeature;

/**
 * Example of authentication of service with http signatures, using configuration file as much as possible.
 */
@SuppressWarnings("DuplicatedCode")
public class SignatureExampleConfigMain {

    private SignatureExampleConfigMain() {
    }

    /**
     * Starts this example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();
        server.context().register(server);

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %1d ms

                Signature example: from config

                Users:
                jack/password in roles: user, admin
                jill/password in roles: user
                john/password in no roles

                ***********************
                ** Endpoints:        **
                ***********************

                Basic authentication, user role required, will use symmetric signatures for outbound:
                  http://localhost:%2$d/service1
                Basic authentication, user role required, will use asymmetric signatures for outbound:
                  http://localhost:%3$d/service1-rsa

                """, TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS), server.port(), server.port("service2"));
    }

    static void setup(WebServerConfig.Builder server) {
        // as we explicitly configure SecurityHttpFeature, we must disable automated loading of security,
        // as it would add another feature with different configuration
        server.featuresDiscoverServices(false)
                // context is a required pre-requisite of security
                .addFeature(ContextFeature.create())
                .routing(SignatureExampleConfigMain::routing1)
                .putSocket("service2", socket -> socket
                        .routing(SignatureExampleConfigMain::routing2));
    }

    private static void routing2(HttpRouting.Builder routing) {
        // build routing (security is loaded from config)
        Config config = config("service2.yaml");

        // helper method to load both security and web server security from configuration
        routing.addFeature(SecurityHttpFeature.create(config.get("security.web-server")))
                .register(new Service2());
    }

    private static void routing1(HttpRouting.Builder routing) {
        // build routing (security is loaded from config)
        Config config = config("service1.yaml");

        // helper method to load both security and web server security from configuration
        routing.addFeature(SecurityHttpFeature.create(config.get("security.web-server")))
                .register(new Service1());
    }

    private static Config config(String confFile) {
        return Config.builder()
                .sources(ConfigSources.classpath(confFile))
                .build();
    }
}
