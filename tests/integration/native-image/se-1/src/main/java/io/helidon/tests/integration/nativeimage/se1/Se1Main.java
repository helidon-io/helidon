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

package io.helidon.tests.integration.nativeimage.se1;

import java.nio.file.Paths;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.FileSystemWatcher;
import io.helidon.health.HealthCheckResponse;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.helidon.webserver.websocket.WsRouting;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of this integration test.
 */
public final class Se1Main {
    /**
     * Cannot be instantiated.
     */
    private Se1Main() {
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
     * @return the created {@link io.helidon.webserver.WebServer} instance
     */
    static WebServer startServer() {
        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = buildConfig();
        HealthObserver health = HealthObserver.builder()
                .addCheck(() -> HealthCheckResponse.builder()
                        .detail("timestamp",
                                System.currentTimeMillis())
                        .build())
                .build();
        ObserveFeature observe = ObserveFeature.just(health);

        // Get webserver config from the "server" section of application.yaml
        WebServer server = WebServer.builder()
                .port(7076)
                .addFeature(observe)
                .addRouting(createRouting(config))
                .addRouting(WsRouting.builder()
                                    .endpoint("/ws/messages", WebSocketEndpoint::new))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet");

        return server;
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        classpath("application-test.yaml").optional(),
                        file("conf/overrides.yaml")
                                .changeWatcher(FileSystemWatcher.create())
                                .optional(),
                        classpath("application.yaml"))
                .build();
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static HttpRouting.Builder createRouting(Config config) {

        GreetService greetService = new GreetService(config);
        MockZipkinService zipkinService = new MockZipkinService(Set.of("helidon-webclient"));
        WebClientService webClientService = new WebClientService(config, zipkinService);

        return HttpRouting.builder()
                .register("/static/path", StaticContentService.create(Paths.get("web")))
                .register("/static/classpath", StaticContentService.create("web"))
                .register("/static/jar", StaticContentService.create("web-jar"))
                .register("/greet", greetService)
                .register("/wc", webClientService)
                .register("/zipkin", zipkinService);
    }

}
