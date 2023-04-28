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
package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.KeyConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http2.webserver.Http2ConfigDefault;
import io.helidon.nima.http2.webserver.Http2ConnectionProvider;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http1.Http1Route;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.http.Http.Header.USER_AGENT;
import static io.helidon.common.http.Http.Method.GET;
import static io.helidon.common.http.Http.Method.POST;
import static io.helidon.common.http.Http.Method.PUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class Http2WebClientTest {

    private static final Http.HeaderName CLIENT_CUSTOM_HEADER_NAME = Http.Header.create("client-custom-header");
    private static final Http.HeaderName SERVER_CUSTOM_HEADER_NAME = Http.Header.create("server-custom-header");
    private static final Http.HeaderName SERVER_HEADER_FROM_PARAM_NAME = Http.Header.create("header-from-param");
    private static final Http.HeaderName CLIENT_USER_AGENT_HEADER_NAME = Http.Header.create("client-user-agent");
    private static ExecutorService executorService;
    private static int plainPort;
    private static final LazyValue<Http2Client> priorKnowledgeClient = LazyValue.create(() -> Http2Client.builder()
            .priorKnowledge(true)
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build());
    private static final LazyValue<Http2Client> upgradeClient = LazyValue.create(() -> Http2Client.builder()
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build());
    private static int tlsPort;
    private static final LazyValue<Http2Client> tlsClient = LazyValue.create(() -> Http2Client.builder()
            .baseUri("https://localhost:" + tlsPort + "/versionspecific")
            .tls(Tls.builder()
                         .enabled(true)
                         .trustAll(true)
                         .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                         .build())
            .build());

    Http2WebClientTest(WebServer server) {
        this.plainPort = server.port();
        this.tlsPort = server.port("https");
    }

    @SetUpServer
    static void setUpServer(WebServer.Builder serverBuilder) {
        executorService = Executors.newFixedThreadPool(5);

        KeyConfig privateKeyConfig =
                KeyConfig.keystoreBuilder()
                        .keystore(Resource.create("certificate.p12"))
                        .keystorePassphrase("helidon")
                        .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        serverBuilder
                .defaultSocket(builder -> builder.port(-1)
                        .host("localhost")
                )
                .addConnectionProvider(Http2ConnectionProvider.builder()
                                               .http2Config(Http2ConfigDefault.builder()
                                                                    .initialWindowSize(10))
                                               .build())
                .socket("https",
                        builder -> builder.port(-1)
                                .host("localhost")
                                .tls(tls)
                                .receiveBufferSize(4096)
                                .backlog(8192)
                )
                .routing(router -> router
                        .get("/", (req, res) -> res.send("Hello world!"))
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> {
                            res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).value());
                            res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().value("custQueryParam"));
                            res.send("HTTP/2 route");
                        }))
                        .route(Http2Route.route(PUT, "/versionspecific", (req, res) -> {
                            res.header(SERVER_CUSTOM_HEADER_NAME, req.headers().get(CLIENT_CUSTOM_HEADER_NAME).value());
                            res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).value());
                            res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().value("custQueryParam"));
                            res.send("PUT " + req.content().as(String.class));
                        }))
                        .route(Http2Route.route(POST, "/versionspecific", (req, res) -> {
                            res.header(SERVER_CUSTOM_HEADER_NAME, req.headers().get(CLIENT_CUSTOM_HEADER_NAME).value());
                            res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).value());
                            res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().value("custQueryParam"));
                            res.send("POST " + req.content().as(String.class));
                        }))
                        .route(Http2Route.route(GET, "/versionspecific/h2streaming", (req, res) -> {
                            res.status(Http.Status.OK_200);
                            String execId = req.query().value("execId");
                            try (OutputStream os = res.outputStream()) {
                                for (int i = 0; i < 5; i++) {
                                    os.write(String.format(execId + "BAF%03d", i).getBytes());
                                    Thread.sleep(10);
                                }
                            } catch (IOException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                );
    }

    static Stream<Arguments> clientTypes() {
        return Stream.of(
                Arguments.of("priorKnowledge", priorKnowledgeClient),
                Arguments.of("upgrade", upgradeClient),
                Arguments.of("tls", tlsClient)
        );
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientGet(String name, LazyValue<Http2Client> client) {
        try (Http2ClientResponse response = client.get()
                .get()
                .queryParam("custQueryParam", "test-get")
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).value(),
                       is(ClientRequestImpl.USER_AGENT_HEADER.value()));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).value(),
                       is("test-get"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPut(String clientType, LazyValue<Http2Client> client) {

        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(PUT)
                .queryParam("custQueryParam", "test-put")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("PUT " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).value(),
                       is(ClientRequestImpl.USER_AGENT_HEADER.value()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).value(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).value(),
                       is("test-put"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPost(String clientType, LazyValue<Http2Client> client) {

        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(POST)
                .queryParam("custQueryParam", "test-post")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("POST " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).value(),
                       is(ClientRequestImpl.USER_AGENT_HEADER.value()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).value(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).value(),
                       is("test-post"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void multiplexParallelStreamsGet(String clientType, LazyValue<Http2Client> client)
            throws ExecutionException, InterruptedException, TimeoutException {
        Consumer<Integer> callable = id -> {
            try (Http2ClientResponse response = client.get()
                    .get("/h2streaming")
                    .queryParam("execId", id.toString())
                    .request()
            ) {

                InputStream is = response.inputStream();
                for (int i = 0; ; i++) {
                    byte[] bytes = is.readNBytes("0BAF000".getBytes().length);
                    if (bytes.length == 0) {
                        break;
                    }
                    String message = new String(bytes);
                    assertThat(message, is(String.format(id + "BAF%03d", i)));
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> callable.accept(1), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(2), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(3), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(4), executorService)
        ).get(5, TimeUnit.MINUTES);
    }
}
