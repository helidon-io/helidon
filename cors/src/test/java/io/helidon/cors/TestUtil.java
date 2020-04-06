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
package io.helidon.cors;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.cors.CORSTestServices.CORSTestService;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static io.helidon.common.http.Http.Header.ORIGIN;
import static io.helidon.cors.CORSTestServices.SERVICE_1;
import static io.helidon.cors.CORSTestServices.SERVICE_2;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_MAX_AGE;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.cors.CrossOriginConfig.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.cors.CustomMatchers.notPresent;
import static io.helidon.cors.CustomMatchers.present;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        ServerConfiguration serverConfig = ServerConfiguration.builder(config)
                .port(port)
                .build();
        return WebServer.create(serverConfig, routingBuilder).start().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    static Routing.Builder prepRouting() {
        /*
         * Use the default config for the service at "/greet."
         */
        Config config = minimalConfig();
        CORSSupport.Builder corsSupportBuilder = CORSSupport.builder().config(config.get(CrossOriginHelper.CORS_CONFIG_KEY));

        /*
         * Load a specific config for "/othergreet."
         */
        Config twoCORSConfig = minimalConfig(ConfigSources.classpath("twoCORS.yaml"));
        CORSSupport.Builder twoCORSSupportBuilder =
                CORSSupport.builder().config(twoCORSConfig.get(CrossOriginHelper.CORS_CONFIG_KEY));

        Routing.Builder builder = Routing.builder()
                .register(GREETING_PATH, corsSupportBuilder.build(), new GreetService())
                .register(OTHER_GREETING_PATH, twoCORSSupportBuilder.build(), new GreetService("Other Hello"));

        return builder;
    }

    private static Config minimalConfig(Supplier<? extends ConfigSource> configSource) {
        Config.Builder builder = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource();
        if (configSource != null) {
            builder.sources(configSource);
        }
        return builder.build();
    }

    private static Config minimalConfig() {
        return minimalConfig(null);
    }

    static WebClient startupClient(WebServer server) {
        return WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
    }

    static WebClientResponse runTest1PreFlightAllowedOrigin(WebClient client, String prefix, String origin) throws ExecutionException,
            InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(path(prefix, SERVICE_1));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, origin);
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        return res;
    }

    static void test2PreFlightAllowedHeaders1(WebClient client, String prefix, String origin, String headerToCheck) throws ExecutionException, InterruptedException {
        WebClientRequestBuilder reqBuilder = client
                .method(Http.Method.OPTIONS.name())
                .path(path(prefix, SERVICE_2));

        Headers headers = reqBuilder.headers();
        headers.add(ORIGIN, origin);
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, headerToCheck);

        WebClientResponse res = reqBuilder
                .request()
                .toCompletableFuture()
                .get();

        assertThat(res.status(), is(Http.Status.OK_200));
        assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_ORIGIN), present(is(origin)));
        assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_CREDENTIALS), present(is("true")));
        assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_METHODS), present(is("PUT")));
        assertThat(res.headers()
                .first(ACCESS_CONTROL_ALLOW_HEADERS), present(containsString(headerToCheck)));
        assertThat(res.headers()
                .first(ACCESS_CONTROL_MAX_AGE), notPresent());
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
