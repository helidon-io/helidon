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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class TestUi {

    private static final String GREETING_OPENAPI_PATH = "/openapi-greeting";

    private static final Config OPENAPI_CONFIG_DISABLED_CORS = Config.create(
            ConfigSources.classpath("serverNoCORS.properties").build()).get(OpenAPISupport.Builder.CONFIG_KEY);

    static final Supplier<OpenAPISupport.Builder<?>> GREETING_OPENAPI_SUPPORT_BUILDER_SUPPLIER
            = () -> OpenAPISupport.builder()
            .staticFile("src/test/resources/openapi-greeting.yml")
            .webContext(GREETING_OPENAPI_PATH)
            .config(OPENAPI_CONFIG_DISABLED_CORS);

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
    void checkMinimalUi() throws Exception {

        WebClientResponse response = sharedWebClient.get()
                .path(GREETING_OPENAPI_PATH + OpenApiUi.DEFAULT_UI_WEB_SUBCONTEXT)
                .accept(MediaType.TEXT_HTML)
                .submit()
                .await(15, TimeUnit.SECONDS);

        assertThat("Response code from U/I", response.status().code(), is(200));
        assertThat("Media type returned from U/I",
                   response.headers().contentType(),
                   OptionalMatcher.value(MediaTypeMatcher.test(MediaType.TEXT_HTML)));

        String output = response.content().as(String.class).get();
        assertThat("Plain text document", output, Matchers.allOf(
                Matchers.containsString("openapi: 3"),
                Matchers.containsString("<html")));
    }

    @Test
    void checkHtmlRejectedFromMainEndpoint() throws Exception {
        WebClientResponse response = sharedWebClient.get()
                .path(GREETING_OPENAPI_PATH)
                .accept(MediaType.TEXT_HTML)
                .submit()
                .await(15, TimeUnit.SECONDS);

        assertThat("Status accepting HTML to main endpoint",
                   response.status().code(),
                   is(Http.Status.NOT_FOUND_404.code()));
    }

    @Test
    void checkPlainTextResponse() throws ExecutionException, InterruptedException {
        WebClientResponse response = sharedWebClient.get()
                .path(GREETING_OPENAPI_PATH)
                .accept(MediaType.TEXT_PLAIN)
                .submit()
                .await(Duration.ofSeconds(15));
        assertThat("Status accessing U/I for PLAIN", response.status().code(), is(200));
        assertThat("Returned media type accessing U/I",
                   response.headers().contentType(),
                   OptionalMatcher.value(MediaTypeMatcher.test(MediaType.TEXT_PLAIN)));
        assertThat("U/I PLAIN response content",
                   response.content().as(String.class).get(),
                   allOf(not(containsString("<html")),
                         containsString("openapi: 3")));

    }
}
