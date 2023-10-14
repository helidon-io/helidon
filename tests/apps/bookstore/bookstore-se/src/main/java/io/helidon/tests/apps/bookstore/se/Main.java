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

package io.helidon.tests.apps.bookstore.se;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.health.checks.DeadlockHealthCheck;
import io.helidon.health.checks.DiskSpaceHealthCheck;
import io.helidon.health.checks.HeapMemoryHealthCheck;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.http.media.jsonb.JsonbSupport;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.observe.metrics.MetricsObserver;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    private static final String SERVICE_PATH = "/books";

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
        WebServerConfig.Builder serverBuilder = WebServerConfig.builder();
        setupServer(serverBuilder, ssl);

        WebServer server = serverBuilder.build();
        server.start();
        String url = (ssl ? "https" : "http") + "://localhost:" + server.port() + SERVICE_PATH;
        System.out.println("WEB server is up! " + url + " [ssl=" + ssl + ", http2=" + http2
                                   + ", compression=" + compression + "]");

        return server;
    }

    static void setupServer(WebServerConfig.Builder serverBuilder, boolean ssl) {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        HealthObserver health = HealthObserver.builder()
                .useSystemServices(false)
                .addCheck(HeapMemoryHealthCheck.create())
                .addCheck(DiskSpaceHealthCheck.create())
                .addCheck(DeadlockHealthCheck.create())
                .details(true)
                .endpoint("/health")
                .build();
        MetricsObserver metrics = MetricsObserver.builder()
                .endpoint("/metrics")
                .build();

        // Build server config based on params
        serverBuilder
                // Health at "/health", and metrics at "/metrics"
                .addFeature(ObserveFeature.just(health, metrics))
                .addRouting(createRouting(config))
                .config(config.get("server"))
                .update(it -> configureJsonSupport(it, config))
                .update(it -> configureSsl(it, ssl));
    }

    static JsonLibrary getJsonLibrary(Config config) {
        return config.get("app.json-library")
                .asString()
                .map(String::toUpperCase)
                .map(JsonLibrary::valueOf)
                .orElse(JsonLibrary.JSONP);
    }

    private static void configureJsonSupport(WebServerConfig.Builder wsBuilder, Config config) {
        JsonLibrary jsonLibrary = getJsonLibrary(config);

        wsBuilder.mediaContext(context -> {
            context.mediaSupportsDiscoverServices(false);
            switch (jsonLibrary) {
            case JSONP -> context.addMediaSupport(JsonpSupport.create(config));
            case JSONB -> context.addMediaSupport(JsonbSupport.create(config));
            case JACKSON -> context.addMediaSupport(JacksonSupport.create(config));
            default -> throw new RuntimeException("Unknown JSON library " + jsonLibrary);
            }
        });
    }

    private static void configureSsl(WebServerConfig.Builder wsBuilder, boolean useSsl) {
        if (!useSsl) {
            return;
        }

        Keys privateKeyConfig = privateKey();
        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        wsBuilder.tls(tls);
    }

    private static Keys privateKey() {
        String password = "helidon";

        return Keys.builder()
                .keystore(keystore -> keystore.keystore(Resource.create("certificate.p12"))
                        .passphrase(password))
                .build();
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static HttpRouting.Builder createRouting(Config config) {

        return HttpRouting.builder()
                .register(SERVICE_PATH, new BookService(config));
    }

    enum JsonLibrary {
        JSONP,
        JSONB,
        JACKSON
    }
}
