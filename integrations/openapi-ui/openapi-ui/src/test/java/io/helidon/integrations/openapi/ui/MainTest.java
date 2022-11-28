/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.openapi.ui;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.LogConfig;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.openapi.OpenApiUi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

class MainTest {

    private static final String RUN_BROWSER_TEST_PROPERTY = "io.helidon.openapi.ui.test.runForBrowser";

    /**
     * Runs the server for as many minutes as specified by the property {@value RUN_BROWSER_TEST_PROPERTY}
     * so you could use a browser to test the U/I. If the property is present but not set to a value, the default is 5 minutes.
     * If the property is not set, the test is skipped. For example, add
     *
     * {@code -Dsurefire.argLine="-Dio.helidon.openapi.ui.test.runForBrowser"}
     *
     * to the mvn command line to enable the test to use the default wait time.
     *
     * Use {@code -Dsurefire.argLine="-Dio.helidon.openapi.ui.test.runForBrowser=8"} to wait for 8 minutes.
     *
     * @throws InterruptedException in case of problems waiting for the time to pass
     */
    @Test
    @EnabledIfSystemProperty(named = RUN_BROWSER_TEST_PROPERTY, matches=".*")
    void browserDefaults() throws InterruptedException {
        browser(null, null, null, null);
    }

    @Test
    void testMainUiPageWithDefaultSettings() {
        // Use defaults for the OpenAPI endpoint and the U/I endpoint.
        loadAndCheckMainPage(null, // load config from normal sources
                             null,
                             null, // create OpenAPISupport from config
                             null, // create OpenApiUiFull from config
                             OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT
        );
    }

    @Test
    void testAlternateUiPath() {
        // Alternate web-context for the U/I, default for the OpenAPI document endpoint.
        Map<String, String> settings = Map.of("openapi.ui.web-context", "/openapi-ui-x");
        Config config = Config.create(ConfigSources.create(settings));
        loadAndCheckMainPage(config,
                             null,
                             null,
                             null,
                             "/openapi-ui-x");
    }

    @Test
    void testAlternateOpenApiPath() {
        // Alternate web-context for OpenAPI, default for the U/I.
        // The U/I should automatically take into account the non-default path for the OpenAPI document.
        Map<String, String> settings = Map.of("openapi.web-context", "/openapi-x");
        Config config = Config.create(ConfigSources.create(settings));
        loadAndCheckMainPage(config,
                             null,
                             null,
                             null,
                             "/openapi-x" + OpenApiUi.UI_WEB_SUBCONTEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/openapi", "/openapi/"})
    void testRedirect(String testPath) {
        loadAndCheckMainPage(null, null, null, null,
                             testPath, false, 307);
    }

    static void loadAndCheckMainPage(Config config,
                                     OpenAPISupport.Builder<?> openAPISupportBuilder,
                                     OpenAPISupport openAPISupport,
                                     OpenApiUi.Builder<?, ?> uiSupportBuilder,
                                     String uiPathToCheck) {
        loadAndCheckMainPage(config, openAPISupportBuilder, openAPISupport, uiSupportBuilder, uiPathToCheck, true, 200);
    }

    static void loadAndCheckMainPage(Config config,
                                     OpenAPISupport.Builder<?> openAPISupportBuilder,
                                     OpenAPISupport openAPISupport,
                                     OpenApiUi.Builder<?, ?> uiSupportBuilder,
                                     String uiPathToCheck,
                                     boolean followRedirects,
                                     int expectedStatus) {
        run (config,
             openAPISupportBuilder,
             openAPISupport,
             uiSupportBuilder,
             uiPathToCheck,
             webClient ->
                     webClient.get()
                             .followRedirects(followRedirects)
                             .accept(MediaType.TEXT_HTML),
             expectedStatus,
             webClientResponse -> {
                String html = null;
                 try {
                     html = webClientResponse.content()
                             .as(String.class)
                             .get(5, TimeUnit.SECONDS);
                 } catch (InterruptedException | ExecutionException | TimeoutException e) {
                     fail("Error retrieving initial U/I page", e);
                 }

                 // Don't inspect the HTML much; we don't want this test to depend too heavily on the U/I impl.
                 if (expectedStatus == 200) {
                     assertThat("Returned HTML from U/I", html, containsString("https://helidon.io"));
                 }
             });
    }

    static void run(Config config,
                    OpenAPISupport.Builder<?> openApiSupportBuilder,
                    OpenAPISupport openApiSupport,
                    OpenApiUi.Builder<?, ?> uiSupportBuilder,
                    String uiPathToTest,
                    Function<WebClient, WebClientRequestBuilder> operation,
                    int expectedStatus,
                    Consumer<WebClientResponse> responseConsumer) {
        WebServer server = null;

        try {
            server = startServer(config, openApiSupportBuilder, openApiSupport, uiSupportBuilder).get();
            String baseUri = "http://localhost:" + server.port();
            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUri(baseUri);

            WebClient webClient = webClientBuilder.build();
            System.out.printf("Checking %s%s%n", baseUri, uiPathToTest);
            WebClientResponse webClientResponse = operation.apply(webClient)
                    .path(uiPathToTest)
                    .request()
                    .await(5, TimeUnit.SECONDS);

            assertThat("Status code in response getting main page",
                       webClientResponse.status().code(), is(expectedStatus));

            try {
                responseConsumer.accept(webClientResponse);
            } catch (Exception e) {
                fail("Error consuming response", e);
            } finally {
                webClientResponse.close();
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            fail("Error starting webserver", e);
        } finally {
            if (server != null) {
                server.shutdown().await(5, TimeUnit.SECONDS);
            };
        }
    }

    static void browser(Config config,
                        OpenAPISupport.Builder<?> openApiSupportBuilder,
                        OpenAPISupport openApiSupport,
                        OpenApiUi.Builder<?, ?> uiSupportBuilder) {
        String minutesToStayUpText = System.getProperty(RUN_BROWSER_TEST_PROPERTY);
        if (minutesToStayUpText == null) {
            return;
        }
        // If the property is defined but not given a value, Java reports "true".
        if (Boolean.parseBoolean(minutesToStayUpText)) {
            minutesToStayUpText = "5";
        }
        long minutesToStayUp = (minutesToStayUpText.length() > 0 ? Long.parseUnsignedLong(minutesToStayUpText) : 5);

        if (config == null) {
            config = Config.create();
        }
        Map<String, String> portConfig = Map.of("server.port", "8080");
        Config configWithPort = Config.create(ConfigSources.create(portConfig), ConfigSources.create(config));
        WebServer webServer = null;

        try {
            try {
                // For the browser test set the server.port to 8080 so the human knows where to find the pages.

                webServer = startServer(configWithPort, openApiSupportBuilder, openApiSupport, uiSupportBuilder).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail("Error starting webserver for browser test", e);
            }
            try {
                Thread.sleep(minutesToStayUp * 60 * 1000);
            } catch (InterruptedException e) {
                fail("Error while waiting for timed test to end", e);
            }
        } finally {
            if (webServer != null) {
                webServer.shutdown().await(5, TimeUnit.SECONDS);
            }
        }
    }

    static Single<WebServer> startServer(Config config,
                                         OpenAPISupport.Builder<?> openApiSupportBuilder,
                                         OpenAPISupport openApiSupport,
                                         OpenApiUi.Builder<?, ?> uiSupportBuilder) throws IOException {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        if (config == null) {
            config = Config.create();
        }

        // Get webserver config from the "server" section of application.yaml and register JSON support
        Single<WebServer> server = WebServer.builder(createRouting(config, openApiSupportBuilder, openApiSupport, uiSupportBuilder))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build()
                .start();

        server.thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(()
                                                      -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
        return server;
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @param config configuration of this server
     * @param openApiSupport {@code OpenAPISupport} instance to register with routing; null -> create one using config
     * @param uiSupportBuilder {@code OpenApiUiFull.Builder} instance to use in registering with routing;
     *                                                            null -> create one using config
     * @return routing configured with a health check, and a service
     */
    private static Routing createRouting(Config config,
                                         OpenAPISupport.Builder<?> openApiSupportBuilder,
                                         OpenAPISupport openApiSupport,
                                         OpenApiUi.Builder<?, ?> uiSupportBuilder) throws IOException {

        if (uiSupportBuilder == null) {
            uiSupportBuilder = OpenApiUiFull.builder()
                    .config(config.get(OpenAPISupport.Builder.CONFIG_KEY)
                                    .get(OpenApiUi.Builder.OPENAPI_UI_CONFIG_KEY));
        }
        if (openApiSupportBuilder == null) {
            openApiSupportBuilder = OpenAPISupport.builder()
                    .config(config.get(OpenAPISupport.Builder.CONFIG_KEY));
        }

        if (openApiSupport == null) {
            openApiSupportBuilder.ui(uiSupportBuilder);
            openApiSupport = openApiSupportBuilder.build();
        }

        GreetService greetService = new GreetService(config);
        Routing.Builder routingBuilder = Routing.builder()
                .register(openApiSupport)
                .register("/greet", greetService);
        return routingBuilder.build();
    }
}
