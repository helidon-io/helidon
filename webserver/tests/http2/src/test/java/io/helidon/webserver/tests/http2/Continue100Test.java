/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.http2;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class Continue100Test {
    private static final System.Logger LOGGER = System.getLogger(Continue100Test.class.getName());
    private static final long TIMEOUT_MILLIS = Duration.ofSeconds(10).toMillis();
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Vertx VERTX = Vertx.vertx();
    private static final HttpClient VERTX_CLIENT =
            VERTX.createHttpClient(new HttpClientOptions()
                                           .setProtocolVersion(HttpVersion.HTTP_2)
                                           .setHttp2ClearTextUpgrade(false)
                                           .setSsl(false));

    private static final AtomicBoolean CLIENT_SENT_DATA = new AtomicBoolean(false);
    private final Http2Client webClient;

    Continue100Test(WebServer server) {
        webClient = Http2Client.builder()
                .baseUri(URI.create("http://localhost:" + server.port()))
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .build();
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(GET, "/ping", (req, res) -> res.send("pong")));
        router.route(Http2Route.route(POST, "/100-continue-not", (req, res) -> res.status(418).send()));
        router.route(Http2Route.route(POST, "/100-continue",
                                      (req, res) -> {
                                          try {
                                              Thread.sleep(200);
                                          } catch (InterruptedException e) {
                                              LOGGER.log(System.Logger.Level.INFO, "100 test interrupted", e);
                                          }
                                          if (CLIENT_SENT_DATA.get()) {
                                              res.send("Client didn't wait for server's 100 continue!");
                                              return;
                                          }
                                          try (var is = req.content().inputStream()) {
                                              byte[] content = is.readAllBytes();
                                              res.send(content);
                                          }
                                      }
        ));
    }

    @BeforeEach
    void beforeEach() {
        CLIENT_SENT_DATA.set(false);
    }

    @Test
    void vertxClient(WebServer server) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<HttpClientResponse> finished = new CompletableFuture<>();
        VERTX_CLIENT.request(HttpMethod.POST, server.port(), "localhost", "/100-continue")
                .onSuccess(request -> {
                    request.response().onSuccess(response -> {
                        response.end();
                        response.body().onSuccess(event -> finished.complete(response));
                    }).onFailure(finished::completeExceptionally);

                    request.putHeader(HeaderNames.EXPECT.defaultCase(), HeaderValues.EXPECT_100.get());

                    request.continueHandler(v -> {
                        // OK to send rest of body
                        request.putHeader(HeaderNames.CONTENT_LENGTH.defaultCase(), String.valueOf(DATA.length()));
                        CLIENT_SENT_DATA.set(true);
                        request.write(DATA);
                        request.end();
                    });

                    request.sendHead();
                });

        assertThat(finished.get(TIMEOUT_MILLIS, MILLISECONDS).statusCode(), is(Status.OK_200.code()));
        assertThat(finished.get(TIMEOUT_MILLIS, MILLISECONDS).body().result().toString(), is(DATA));
    }

    @Test
    void vertxClientTeapot(WebServer server) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<HttpClientResponse> finished = new CompletableFuture<>();
        VERTX_CLIENT.request(HttpMethod.POST, server.port(), "localhost", "/100-continue-not")
                .onSuccess(request -> {
                    request.response().onSuccess(response -> {
                        response.end();
                        response.body().onSuccess(event -> finished.complete(response));
                    }).onFailure(finished::completeExceptionally);

                    request.putHeader(HeaderNames.EXPECT.defaultCase(), HeaderValues.EXPECT_100.get());

                    request.continueHandler(v -> {
                        // OK to send rest of body
                        request.putHeader(HeaderNames.CONTENT_LENGTH.defaultCase(), String.valueOf(DATA.length()));
                        CLIENT_SENT_DATA.set(true);
                        request.write(DATA);
                        request.end();
                    });

                    request.sendHead();
                });

        assertThat(finished.get(TIMEOUT_MILLIS, MILLISECONDS).statusCode(), is(Status.I_AM_A_TEAPOT_418.code()));
    }

    @Test
    void webclientExpect() {
        try (Http2ClientResponse res = webClient
                .post("/100-continue")
                .header(HeaderValues.EXPECT_100)
                .outputStream(out -> {
                    out.write(DATA.getBytes());
                    CLIENT_SENT_DATA.set(true);
                    out.write(DATA.getBytes());
                    out.close();
                })) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.entity().as(String.class), is(DATA.repeat(2)));
        }
    }

    @Test
    void webclientNoExpect() {
        try (Http2ClientResponse res = webClient
                .post("/100-continue")
                .outputStream(out -> {
                    out.write(DATA.getBytes());
                    out.close();
                })) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.entity().as(String.class), is(DATA));
        }
    }

    @Test
    void webclientTeapot() {
        try (Http2ClientResponse res = webClient
                .post("/100-continue-not")
                .header(HeaderValues.EXPECT_100)
                .outputStream(out -> {
                    CLIENT_SENT_DATA.set(true);
                    out.write(DATA.getBytes());
                    out.close();
                })) {
            assertThat(res.status(), is(Status.I_AM_A_TEAPOT_418));
        }
    }
}
