/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.webserver.cors.CORSTestServices.CORSTestService;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import static io.helidon.webserver.cors.CORSTestServices.SERVICE_3;

public class TestUtil {

    static final String GREETING_PATH = "/greet";
    static final String OTHER_GREETING_PATH = "/othergreet";

    static WebServer startupServerWithApps() throws InterruptedException, ExecutionException, TimeoutException {
        Routing.Builder routingBuilder = TestUtil.prepRouting();
        return startServer(0, routingBuilder);
    }

    private static WebServer startServer(int port, Routing.Builder routingBuilder) throws InterruptedException,
            ExecutionException, TimeoutException {
        Config config = Config.create();
        ServerConfiguration serverConfig = ServerConfiguration.builder(config.get("server"))
                .port(port)
                .build();
        return WebServer.create(serverConfig, routingBuilder).start().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    static Routing.Builder prepRouting() {
        CrossOriginConfig cors3COC= CrossOriginConfig.builder()
                .allowOrigins("http://foo.bar", "http://bar.foo")
                .allowMethods("DELETE", "PUT")
                .build();

        /*
         * Use the default config for the service at "/greet" and then programmatically add the config for /cors3.
         */
        CORSSupport.Builder corsSupportBuilder = CORSSupportSE.builder();
        corsSupportBuilder.addCrossOrigin(SERVICE_3.path(), cors3COC);

        /*
         * Load a specific config for "/othergreet."
         */
        Config twoCORSConfig = minimalConfig(ConfigSources.classpath("twoCORS.yaml"));

        Routing.Builder builder = Routing.builder()
                .register(GREETING_PATH,
                          CORSSupportSE.builder().config(Config.create().get("cors-setup")).build(),
                          new GreetService())
                .register(OTHER_GREETING_PATH,
                          CORSSupportSE.builder().config(twoCORSConfig.get("cors-2-setup")).build(),
                          new GreetService("Other Hello"))
                .any(TestHandlerRegistration.CORS4_CONTEXT_ROOT,
                        CORSSupportSE.builder()
                                .allowOrigins("http://foo.bar", "http://bar.foo")
                                .allowMethods("PUT")
                                .build(),
                        (req, resp) -> resp.status(Http.Status.OK_200).send())
                .get(TestHandlerRegistration.CORS4_CONTEXT_ROOT,
                        CORSSupportSE.builder()
                                .allowOrigins("*")
                                .allowMethods("GET")
                                .build(),
                        (req, resp) -> resp.status(Http.Status.OK_200).send());

        return builder;
    }

    private static Config minimalConfig(Supplier<? extends ConfigSource> configSource) {
        Config.Builder configBuilder = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();
        configBuilder.sources(configSource);
        return configBuilder.build();
    }

    private static Config minimalConfig() {
        return minimalConfig(null);
    }

    static WebClient startupClient(WebServer server) {
        return WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    /**
     * Shuts down the specified web server.
     *
     * @param ws the {@code WebServer} instance to stop
     */
    public static void shutdownServer(WebServer ws) {
        if (ws != null) {
            try {
                stopServer(ws);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new RuntimeException("Error shutting down server for test", ex);
            }
        }
    }

    /**
     * Stop the web server.
     *
     * @param server the {@code WebServer} to stop
     * @throws InterruptedException if the stop operation was interrupted
     * @throws ExecutionException if the stop operation failed as it ran
     * @throws TimeoutException if the stop operation timed out
     */
    public static void stopServer(WebServer server) throws
            InterruptedException, ExecutionException, TimeoutException {
        if (server != null) {
            server.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    static String path(CORSTestService testService) {
        return GREETING_PATH + testService.path();
    }

    static String path(String prefix, CORSTestService testService) {
        return prefix + testService.path();
    }
}
