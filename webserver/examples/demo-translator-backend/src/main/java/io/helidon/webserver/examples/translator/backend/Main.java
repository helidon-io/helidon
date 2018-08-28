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

package io.helidon.webserver.examples.translator.backend;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;
import io.helidon.webserver.zipkin.ZipkinTracerBuilder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Demo Backend Example Application main class.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    /**
     * (design) HideUtilityClassConstructor: Utility classes should not have a public or default constructor.
     */
    private Main() {
    }

    /**
     * Creates new instance of {@link WebServer} configured for this service.
     *
     * @param configuration the server configuration
     * @return new {@link WebServer} instance.
     */
    public static WebServer createBackendWebServer(ServerConfiguration configuration) {

        Routing.Builder router = Routing.builder()
                .register(JerseySupport.create(new Application()));

        if (!isSecurityDisabled()) {
            // TODO Add security
            //            builder.addModule(SecurityModule.builder()
            //                                      .enforceAuthentication("/translator/*")
            //                                      .skipAuthentication("/translator/break")
            //                                      .skipAuthentication("/translator/fix")
            //                                      .skipAuthentication("/translator/health")
            //                                      .build());
        } else {
            LOGGER.info("[dev-local] Running without Security module.");
        }
        return WebServer.create(configuration, router.build());
    }

    /**
     * Is the application running in local development mode?
     *
     * @return {@code true} if the application is running locally.
     */
    public static boolean isSecurityDisabled() {
        // TODO enable security return System.getenv("MIC_DEV_LOCAL") != null;
        return true;
    }

    /**
     * The main method of Translator backend.
     *
     * @param args command-line args, currently ignored.
     * @throws Exception in case of an error
     */
    public static void main(String[] args) throws Exception {
        // configure logging in order to not have the standard JVM defaults
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));

        dumpEnv();

        long initNanoTime = System.nanoTime();

        WebServer webServer = createBackendWebServer(
                ServerConfiguration.builder()
                        .port(9080)
                        .tracer(ZipkinTracerBuilder.forService("helidon-webserver-translator-backend")
                                        .zipkin(System.getenv().getOrDefault("ODX_AURA_ZIPKIN_ADDRESS", null))
                                        .build())
                        .build())
                .start()
                .toCompletableFuture()
                .get(10, SECONDS);

        System.out.println("Translator backend started in " + MILLISECONDS
                .convert(System.nanoTime() - initNanoTime, NANOSECONDS) + " ms.");
        System.out.println("Webserver running at http://localhost:" + webServer.port());
    }

    private static void dumpEnv() {
        Map<String, String> sortedEnvVars = new TreeMap<>(System.getenv());

        System.out.println("Environment variables:");
        sortedEnvVars.forEach((key, value) -> System.out.println(key + "=" + value));
        System.out.println("---");
    }

}
