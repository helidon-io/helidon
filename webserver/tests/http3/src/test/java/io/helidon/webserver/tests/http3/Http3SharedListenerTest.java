/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.http3;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http3.Http3Route;
import io.helidon.webserver.security.SecurityFeature;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3SharedListenerTest {
    private static final String MISSING = "missing";
    private static final AtomicInteger SECURITY_INVOCATIONS = new AtomicInteger();
    private static final AtomicReference<List<String>> SECURITY_HEADER_VALUES = new AtomicReference<>();

    @Test
    void shouldServeSharedRouteOverHttp1AndHttp3() throws Exception {
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(Http3SharedListenerTest::routing);
             HttpClient http1Client = environment.http1Client();
             HttpClient http3Client = environment.http3Client()) {
            int port = environment.port();

            HttpResponse<String> http1Response =
                    http1Client.send(Http3TestSupport.http1Get(port, "/hello"), ofString());
            HttpResponse<String> http3Response =
                    http3Client.send(Http3TestSupport.http3Get(port, "/hello"), ofString());

            assertThat(http1Response.statusCode(), is(200));
            assertThat(http1Response.version(), is(HTTP_1_1));
            assertThat(http1Response.body(), is("shared"));

            assertThat(http3Response.statusCode(), is(200));
            assertThat(http3Response.version(), is(HTTP_3));
            assertThat(http3Response.body(), is("shared"));
        }
    }

    @Test
    void shouldUseFirstMatchingRouteOrderForHttp3Route() throws Exception {
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(Http3SharedListenerTest::routing);
             HttpClient http1Client = environment.http1Client();
             HttpClient http3Client = environment.http3Client()) {
            int port = environment.port();

            HttpResponse<String> http3SharedFirst =
                    http3Client.send(Http3TestSupport.http3Get(port, "/shared-first"), ofString());
            HttpResponse<String> http3Http3First =
                    http3Client.send(Http3TestSupport.http3Get(port, "/http3-first"), ofString());
            HttpResponse<String> http1Http3First =
                    http1Client.send(Http3TestSupport.http1Get(port, "/http3-first"), ofString());

            assertThat(http3SharedFirst.statusCode(), is(200));
            assertThat(http3SharedFirst.version(), is(HTTP_3));
            assertThat(http3SharedFirst.body(), is("shared-first"));

            assertThat(http3Http3First.statusCode(), is(200));
            assertThat(http3Http3First.version(), is(HTTP_3));
            assertThat(http3Http3First.body(), is("http3-first"));

            assertThat(http1Http3First.statusCode(), is(200));
            assertThat(http1Http3First.version(), is(HTTP_1_1));
            assertThat(http1Http3First.body(), is("shared-after-http3"));
        }
    }

    @Test
    void shouldIgnoreNetworkConnectionNameHeaderInHttp3Request() throws Exception {
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(Http3SharedListenerTest::routing);
             Http3TestSupport.LowLevelHttp3Client client = environment.lowLevelHttp3Client()) {
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create("x-helidon-cn", "spoofed-client"));
            Http3TestSupport.DecodedResponse response = client.request(environment.uri("/client-cn"),
                                                                      "GET",
                                                                      headers);

            assertThat(response.status(), is(200));
            assertThat(entity(response), is(MISSING));
        }
    }

    @Test
    void shouldIgnoreNetworkConnectionNameHeaderBeforeHttp3Security() throws Exception {
        SECURITY_INVOCATIONS.set(0);
        SECURITY_HEADER_VALUES.set(null);
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(Http3SharedListenerTest::security,
                                                     Http3SharedListenerTest::routing);
             Http3TestSupport.LowLevelHttp3Client client = environment.lowLevelHttp3Client()) {
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create("x-helidon-cn", "spoofed-client"));
            Http3TestSupport.DecodedResponse response = client.request(environment.uri("/security-cn"),
                                                                      "GET",
                                                                      headers);

            assertThat(response.status(), is(200));
            assertThat(entity(response), is(MISSING));
            assertThat(SECURITY_INVOCATIONS.get(), is(1));
        }
    }

    private static void routing(HttpRouting.Builder router) {
        router.get("/hello", (_, res) -> res.send("shared"))
                .get("/client-cn", (req, res) -> res.send(req.headers()
                                                                  .value(HeaderNames.X_HELIDON_CN)
                                                                  .orElse(MISSING)))
                .get("/security-cn",
                     SecurityFeature.authenticate(),
                     (_, res) -> {
                         List<String> values = SECURITY_HEADER_VALUES.get();
                         res.send(values == null ? MISSING : String.join("|", values));
                     })
                .route(HttpRoute.builder()
                               .methods(GET)
                               .path("/shared-first")
                               .handler((_, res) -> res.send("shared-first"))
                               .build())
                .route(Http3Route.route(GET, "/shared-first", (_, res) -> res.send("http3-after-shared")))
                .route(Http3Route.route(GET, "/http3-first", (_, res) -> res.send("http3-first")))
                .route(HttpRoute.builder()
                               .methods(GET)
                               .path("/http3-first")
                               .handler((_, res) -> res.send("shared-after-http3"))
                               .build());
    }

    private static void security(WebServerConfig.Builder server) {
        Security security = Security.builder()
                .addAuthenticationProvider(providerRequest -> {
                    SECURITY_INVOCATIONS.incrementAndGet();
                    SECURITY_HEADER_VALUES.set(providerRequest.env().headers().get(HeaderNames.X_HELIDON_CN_NAME));
                    return AuthenticationResponse.success(Principal.create("jack"));
                })
                .build();

        server.addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(security)
                                    .build());
    }

    private static String entity(Http3TestSupport.DecodedResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
