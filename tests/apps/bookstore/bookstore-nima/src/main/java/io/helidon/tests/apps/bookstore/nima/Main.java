/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.nima;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.health.checks.DeadlockHealthCheck;
import io.helidon.health.checks.DiskSpaceHealthCheck;
import io.helidon.health.checks.HealthChecks;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.jsonp.JsonpMediaSupportProvider;
import io.helidon.nima.observe.health.HealthFeature;
import io.helidon.nima.observe.metrics.MetricsFeature;
import io.helidon.nima.webserver.Routing;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;

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
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static WebServer startServer() {
        return startServer(false, false, false);
    }

    /**
     * Start the server.
     *
     * @param ssl Enable ssl support.
     * @param http2 Enable http2 support.
     * @return the created {@link WebServer} instance
     */
    static WebServer startServer(boolean ssl, boolean http2, boolean compression) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();


        // Build server config based on params
        WebServer.Builder serverBuilder = WebServer.builder()
                .addRouting(createRouting(config))
                .config(config.get("server"))
                .update(it -> configureJsonSupport(it, config))
                .update(it -> configureSsl(it, ssl));
                // .enableCompression(compression);

        configureJsonSupport(serverBuilder, config);

        WebServer server = serverBuilder.build();
        server.start();
        String url = (ssl ? "https" : "http") + "://localhost:" + server.port() + SERVICE_PATH;
        System.out.println("WEB server is up! " + url + " [ssl=" + ssl + ", http2=" + http2
                + ", compression=" + compression + "]");

        return server;
    }

    private static void configureJsonSupport(WebServer.Builder wsBuilder, Config config) {
        JsonLibrary jsonLibrary = getJsonLibrary(config);
        return;

        /* Nima WebServer.Builder does not currently support programmatic way to set media support */
//        switch (jsonLibrary) {
//        case JSONP:
//            wsBuilder.addMediaSupport(JsonpSupport.create());
//            break;
//        case JSONB:
//            wsBuilder.addMediaSupport(JsonbSupport.create());
//            break;
//        case JACKSON:
//            wsBuilder.addMediaSupport(JacksonSupport.create());
//            break;
//        default:
//            throw new RuntimeException("Unknown JSON library " + jsonLibrary);
//        }
    }

    private static void configureSsl(WebServer.Builder wsBuilder, boolean useSsl) {
        if (!useSsl) {
            return;
        }

        KeyConfig privateKeyConfig = privateKey();
        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        wsBuilder.tls(tls);
    }

    private static KeyConfig privateKey() {
        String password = "helidon";

        return KeyConfig.keystoreBuilder()
                .keystore(Resource.create("certificate.p12"))
                .keystorePassphrase(password)
                .build();
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {

        HealthFeature health = HealthFeature.builder()
                .useSystemServices(false)
                .addCheck(HeapMemoryHealthCheck.create())
                .addCheck(DiskSpaceHealthCheck.create())
                .addCheck(DeadlockHealthCheck.create())
                .details(true)
                .build();

        HttpRouting.Builder builder = HttpRouting.builder()
                .addFeature(health)                   // Health at "/health"
                .addFeature(MetricsFeature.builder().build())  // Metrics at "/metrics"
                .register(SERVICE_PATH, new BookService(config));

        return builder.build();
    }

    static JsonLibrary getJsonLibrary(Config config) {
        return config.get("app.json-library")
                .asString()
                .map(String::toUpperCase)
                .map(JsonLibrary::valueOf)
                .orElse(JsonLibrary.JSONP);
    }
}
