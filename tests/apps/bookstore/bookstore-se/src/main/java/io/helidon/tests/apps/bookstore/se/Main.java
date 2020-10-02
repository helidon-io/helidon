/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.se;

import java.io.IOException;
import java.util.logging.LogManager;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jackson.JacksonSupport;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.ExperimentalConfiguration;
import io.helidon.webserver.Http2Configuration;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServerTls;
import io.helidon.webserver.WebServer;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    private static final String SERVICE_PATH = "/books";

    enum JsonLibrary {
        JSONP,
        JSONB,
        JACKSON
    }

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    static WebServer startServer() throws IOException {
        return startServer(false, false);
    }

    /**
     * Start the server.
     *
     * @param ssl Enable ssl support.
     * @param http2 Enable http2 support.
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    static WebServer startServer(boolean ssl, boolean http2) throws IOException {
        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Build server config based on params
        WebServer server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .update(it -> configureJsonSupport(it, config))
                .update(it -> configureSsl(it, ssl))
                .update(it -> configureHttp2(it, http2))
                .build();

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            String url = (ssl ? "https" : "http") + "://localhost:" + ws.port() + SERVICE_PATH;
            System.out.println("WEB server is up! " + url + " [ssl=" + ssl + ", http2=" + http2 + "]");
        });

        // Server threads are not daemon. NO need to block. Just react.
        server.whenShutdown()
                .thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }

    private static void configureJsonSupport(WebServer.Builder wsBuilder, Config config) {
        JsonLibrary jsonLibrary = getJsonLibrary(config);

        switch (jsonLibrary) {
        case JSONP:
            wsBuilder.addMediaSupport(JsonpSupport.create());
            break;
        case JSONB:
            wsBuilder.addMediaSupport(JsonbSupport.create());
            break;
        case JACKSON:
            wsBuilder.addMediaSupport(JacksonSupport.create());
            break;
        default:
            throw new RuntimeException("Unknown JSON library " + jsonLibrary);
        }
    }

    private static void configureHttp2(WebServer.Builder wsBuilder, boolean useHttp2) {
        if (!useHttp2) {
            return;
        }
        wsBuilder.experimental(
                ExperimentalConfiguration.builder()
                        .http2(Http2Configuration.builder().enable(true).build()).build());
    }

    private static void configureSsl(WebServer.Builder wsBuilder, boolean useSsl) {
        if (!useSsl) {
            return;
        }

        wsBuilder.tls(WebServerTls.builder()
                              .privateKey(KeyConfig.keystoreBuilder()
                                                  .keystore(Resource.create("certificate.p12"))
                                                  .keystorePassphrase("helidon".toCharArray())
                                                  .build())
                              .build());
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
                .build();

        return Routing.builder()
                .register(health)                   // Health at "/health"
                .register(MetricsSupport.create())  // Metrics at "/metrics"
                .register(SERVICE_PATH, new BookService(config))
                .build();
    }

    static JsonLibrary getJsonLibrary(Config config) {
        return config.get("app.json-library")
                .asString()
                .map(String::toUpperCase)
                .map(JsonLibrary::valueOf)
                .orElse(JsonLibrary.JSONP);
    }
}
