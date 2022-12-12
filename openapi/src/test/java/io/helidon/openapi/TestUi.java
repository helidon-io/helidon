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
package io.helidon.openapi;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.testing.OptionalMatcher;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestUi {

    private static final String GREETING_OPENAPI_PATH = "/openapi-greeting";

    private static final Config OPENAPI_CONFIG_DISABLED_CORS = Config.create(
            ConfigSources.classpath("serverNoCORS.properties").build()).get(OpenAPISupport.Builder.CONFIG_KEY);

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

    static final Supplier<OpenAPISupport.Builder<?>> GREETING_OPENAPI_SUPPORT_BUILDER_SUPPLIER
            = () -> OpenAPISupport.builder()
            .staticFile("src/test/resources/openapi-greeting.yml")
            .webContext(GREETING_OPENAPI_PATH)
            .config(OPENAPI_CONFIG_DISABLED_CORS);

    private static final String ALTERNATE_UI_WEB_CONTEXT = "/my-ui";
    static final Supplier<OpenAPISupport.Builder<?>> ALTERNATE_UI_WEB_CONTEXT_OPENAPI_SUPPORT_BUILDER_SUPPLIER
            = () -> OpenAPISupport.builder()
            .staticFile("src/test/resources/openapi-greeting.yml")
            .config(OPENAPI_CONFIG_DISABLED_CORS)
            .ui(OpenApiUi.builder()
                        .webContext(ALTERNATE_UI_WEB_CONTEXT));

    private static WebServer sharedWebServer;

    private static WebClient sharedWebClient;

    @BeforeAll
    public static void init() {
        sharedWebServer = TestUtil.startServer(GREETING_OPENAPI_SUPPORT_BUILDER_SUPPLIER.get());
        sharedWebClient = WebClient.builder()
                .baseUri("http://localhost:" + sharedWebServer.port())
                .build();
    }

    @AfterAll
    public static void cleanup() {
        sharedWebServer.shutdown();
    }

    @Test
    void checkNoOpUi() {

        String path = GREETING_OPENAPI_PATH + OpenApiUi.UI_WEB_SUBCONTEXT;
        WebClientResponse response = sharedWebClient.get()
                .path(path)
                .accept(MediaType.TEXT_HTML)
                .submit()
                .await(15, TimeUnit.SECONDS);

        assertThat("Request to " + path + " response status",
                   response.status().code(),
                   is(Http.Status.NOT_FOUND_404.code()));
    }

    @Test
    void checkSimulatedBrowserAccessToMainEndpoint() throws Exception {
        WebClientResponse response = sharedWebClient.get()
                .path(GREETING_OPENAPI_PATH)
                .accept(SIMULATED_BROWSER_ACCEPT)
                .submit()
                .await(15, TimeUnit.SECONDS);

        assertThat("Status simulating browser to main endpoint",
                   response.status().code(),
                   is(Http.Status.OK_200.code()));
        assertThat("Content-Type",
                   response.headers().contentType(),
                   OptionalMatcher.value(is(OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE)));
    }

    @Test
    void checkAlternateUiWebContext() throws ExecutionException, InterruptedException {
        WebServer ws = TestUtil.startServer(ALTERNATE_UI_WEB_CONTEXT_OPENAPI_SUPPORT_BUILDER_SUPPLIER.get());
        try {
            WebClient wc = WebClient.builder()
                    .baseUri("http://localhost:" + ws.port())
                    .build();

            WebClientResponse response = wc.get()
                                            .path(ALTERNATE_UI_WEB_CONTEXT)
                                            .accept(MediaType.TEXT_PLAIN)
                                            .submit()
                                            .await(Duration.ofSeconds(15));
            assertThat("Request to " + ALTERNATE_UI_WEB_CONTEXT + " status",
                       response.status().code(),
                       is(Http.Status.NOT_FOUND_404.code()));

        } finally {
            ws.shutdown();
        }
    }
}
