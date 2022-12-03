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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.testing.OptionalMatcher;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MainTest {

    private static final String RUN_BROWSER_TEST_PROPERTY = "io.helidon.openapi.ui.test.runForBrowser";

    private static final MediaType[] SIMULATED_BROWSER_ACCEPT = new MediaType[] {
            MediaType.TEXT_HTML,
            MediaType.APPLICATION_XHTML_XML,
            MediaType.builder()
                    .type(MediaType.APPLICATION_XML.type())
                    .subtype(MediaType.APPLICATION_XML.subtype())
                    .parameters(Map.of("q", "0.9"))
                    .build(),
            MediaType.builder()
                    .type("image")
                    .subtype("webp")
                    .build(),
            MediaType.builder()
                    .type("image")
                    .subtype("apng")
                    .build(),
            MediaType.builder()
                    .type(MediaType.WILDCARD_VALUE)
                    .subtype(MediaType.WILDCARD_VALUE)
                    .parameters(Map.of("q", "0.8"))
                    .build()
    };
    /**
     * Runs the server for as many minutes as specified by the property {@value RUN_BROWSER_TEST_PROPERTY}
     * so you could use a browser to test the UI. If the property is present but not set to a value, the default is 5 minutes.
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
        browser();
    }

    @Test
    void checkSimulatedBrowserAccessToMainEndpoint() {
        String path = OpenAPISupport.DEFAULT_WEB_CONTEXT;
        run(null,
            path,
            webClient ->
                webClient.get()
                        .followRedirects(true)
                        .accept(SIMULATED_BROWSER_ACCEPT),
                200,
                webClientResponse -> {
                            String text = null;
                            try {
                                text = webClientResponse.content()
                                        .as(String.class)
                                        .get(5, TimeUnit.SECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                fail("Error retrieving " + Arrays.toString(SIMULATED_BROWSER_ACCEPT)
                                             + " from " + path, e);
                            }
                            // Do a cursory check of the returned plain text which is yaml.
                            assertThat("Response media type from path " + path, webClientResponse.headers().contentType(),
                                       OptionalMatcher.value(is(MediaType.TEXT_HTML)));
                            assertThat("Response code from path " + path, webClientResponse.status().code(), is(200));
                            assertThat("Response from path " + path + " as " + MediaType.TEXT_HTML,
                                       text,
                                       allOf(containsString("<html"),
                                             containsString("https://helidon.io")));
                        });

    }

    @Test
    void testMainUiPageWithDefaultSettings() {
        // Use defaults for the OpenAPI endpoint and the UI endpoint.
        loadAndCheckMainPage(null, // load config from normal sources
                             OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT
        );
    }

    @Test
    void testAlternateUiPath() {
        // Alternate web-context for the UI, default for the OpenAPI document endpoint.
        Map<String, String> settings = Map.of("openapi.ui.web-context", "/openapi-ui-x");
        Config config = Config.create(ConfigSources.create(settings));
        loadAndCheckMainPage(config,
                             "/openapi-ui-x");
    }

    @Test
    void testAlternateOpenApiPath() {
        // Alternate web-context for OpenAPI, default for the UI.
        // The UI should automatically take into account the non-default path for the OpenAPI document.
        Map<String, String> settings = Map.of("openapi.web-context", "/openapi-x");
        Config config = Config.create(ConfigSources.create(settings));
        loadAndCheckMainPage(config,
                             "/openapi-x" + OpenApiUi.UI_WEB_SUBCONTEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {OpenAPISupport.DEFAULT_WEB_CONTEXT, OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT})
    void testDisable(String path) {
        Map<String, String> settings = Map.of("openapi.ui.enabled", "false");
        Config config = Config.create(ConfigSources.create(settings));
        loadAndCheckMainPage(config,
                             path,
                             true,
                             404);
    }

    /**
     * Makes sure redirection occurs correctly for HTML at /openapi and /openapi/.
     * @param testPath the path to check
     */
    @ParameterizedTest
    @ValueSource(strings = {"/openapi", "/openapi/"})
    void testRedirect(String testPath) {
        loadAndCheckMainPage(null, testPath, false, 307);
    }

    static void loadAndCheckMainPage(Config config,
                                     String uiPathToCheck) {
        loadAndCheckMainPage(config, uiPathToCheck, true, 200);
    }

    /**
     * Makes sure we get plain and yaml text from /openapi and /openapi/ui.
     * @param mediaType the media type to ask for
     * @param path the path to probe
     */
    @ParameterizedTest
    @MethodSource
    void testWithMediaType(MediaType mediaType, String path) {
        testWithMediaTypeAndPath(mediaType, path);
    }

    @Test
    void simulateUiRetrievalOfOpenAPIDocument() {
        run(null,
            OpenAPISupport.DEFAULT_WEB_CONTEXT,
            webClient -> webClient.get()
                    .followRedirects(true)
                    .accept(MediaType.APPLICATION_JSON, MediaType.WILDCARD),
            200,
            webClientResponse -> {
                String text = null;
                try {
                    text = webClientResponse.content()
                            .as(String.class)
                            .get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    fail("Error retrieving OpenAPI document", e);
                }
                assertThat("Retrieved OpenAPI document",
                           text,
                           allOf(containsString("openapi: 3"),
                                 containsString("title:"),
                                 not(containsString("<html"))));
            });
    }

    static Stream<Arguments> testWithMediaType() {
        return Stream.of(
                arguments(MediaType.TEXT_PLAIN, OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT),
                arguments(MediaType.TEXT_YAML, OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT),
                arguments(MediaType.TEXT_PLAIN, OpenAPISupport.DEFAULT_WEB_CONTEXT),
                arguments(MediaType.TEXT_YAML, OpenAPISupport.DEFAULT_WEB_CONTEXT));
    }

    private void testWithMediaTypeAndPath(MediaType mediaType, String path) {
        run(null,
            path,
            webClient -> webClient.get()
                    .followRedirects(true)
                    .accept(mediaType),
            200,
            webClientResponse -> {
                String text = null;
                try {
                    text = webClientResponse.content()
                            .as(String.class)
                            .get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    fail("Error retrieving " + mediaType + " from " + path, e);
                }
                // Do a cursory check of the returned plain text which is yaml.
                assertThat("Response media type from path " + path, webClientResponse.headers().contentType(),
                           OptionalMatcher.value(is(mediaType)));
                assertThat("Response code from path " + path, webClientResponse.status().code(), is(200));
                assertThat("Response from path " + path + " as " + mediaType,
                           text,
                           allOf(containsString("openapi: 3"),
                                 containsString("title:")));
            });
    }

    static void loadAndCheckMainPage(Config config,
                                     String uiPathToCheck,
                                     boolean followRedirects,
                                     int expectedStatus) {
        run (config,
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
                     fail("Error retrieving initial UI page", e);
                 }

                 // Don't inspect the HTML much; we don't want this test to depend too heavily on the UI impl.
                 if (expectedStatus == 200) {
                     assertThat("Returned HTML from UI", html, containsString("https://helidon.io"));
                 }
             });
    }

    static void run(Config config,
                    String uiPathToTest,
                    Function<WebClient, WebClientRequestBuilder> operation,
                    int expectedStatus,
                    Consumer<WebClientResponse> responseConsumer) {
        WebServer server = null;

        try {
            server = startServer(config).get();
            String baseUri = "http://localhost:" + server.port();
            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUri(baseUri);

            WebClient webClient = webClientBuilder.build();
            System.out.printf("Checking %s%s%n", baseUri, uiPathToTest);
            WebClientRequestBuilder reqBuilder = operation.apply(webClient);
            List<MediaType> acceptedMediaTypes = reqBuilder.headers().acceptedTypes();
            WebClientResponse webClientResponse = reqBuilder
                    .path(uiPathToTest)
                    .request()
                    .await(5, TimeUnit.SECONDS);

            assertThat("Status code in response getting main page from path " + uiPathToTest
                    + " accepting media types " + acceptedMediaTypes,
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

    static void browser() {
        String minutesToStayUpText = System.getProperty(RUN_BROWSER_TEST_PROPERTY);
        if (minutesToStayUpText == null) {
            return;
        }
        // If the property is defined but not given a value, Java reports "true".
        if (Boolean.parseBoolean(minutesToStayUpText)) {
            minutesToStayUpText = "5";
        }
        long minutesToStayUp = (minutesToStayUpText.length() > 0 ? Long.parseUnsignedLong(minutesToStayUpText) : 5);

        Config config = Config.create();
        Map<String, String> portConfig = Map.of("server.port", "8080");
        Config configWithPort = Config.create(ConfigSources.create(portConfig), ConfigSources.create(config));
        WebServer webServer = null;

        try {
            try {
                // For the browser test set the server.port to 8080 so the human knows where to find the pages.

                webServer = startServer(configWithPort).get(5, TimeUnit.SECONDS);
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

    static Single<WebServer> startServer(Config config) throws IOException {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        if (config == null) {
            config = Config.create();
        }

        // Get webserver config from the "server" section of application.yaml and register JSON support
        Single<WebServer> server = WebServer.builder(createRouting(config))
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
     * @return routing configured with a health check, and a service
     */
    private static Routing createRouting(Config config) throws IOException {

        // OpenAPISupport builds an OpenApiUi internally if none is provided.
        OpenAPISupport openApiSupport = OpenAPISupport.builder()
                .config(config.get(OpenAPISupport.Builder.CONFIG_KEY))
                .build();

        GreetService greetService = new GreetService(config);
        Routing.Builder routingBuilder = Routing.builder()
                .register(openApiSupport)
                .register("/greet", greetService);
        return routingBuilder.build();
    }
}
