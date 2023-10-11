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
package io.helidon.webclient.http2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.http.HeaderNames.USER_AGENT;
import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static io.helidon.http.Method.PUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class Http2WebClientTest {

    private static final HeaderName CLIENT_CUSTOM_HEADER_NAME = HeaderNames.create("client-custom-header");
    private static final HeaderName SERVER_CUSTOM_HEADER_NAME = HeaderNames.create("server-custom-header");
    private static final HeaderName SERVER_HEADER_FROM_PARAM_NAME = HeaderNames.create("header-from-param");
    private static final HeaderName CLIENT_USER_AGENT_HEADER_NAME = HeaderNames.create("client-user-agent");
    private static ExecutorService executorService;
    private static int plainPort;
    private static int tlsPort;
    private static Supplier<Http2Client> localCacheClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .connectTimeout(Duration.ofMinutes(10))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> globalCacheClient = () -> Http2Client.builder()
            .shareConnectionCache(true)
            .connectTimeout(Duration.ofMinutes(10))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> priorKnowledgeClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .connectTimeout(Duration.ofMinutes(10))
            .protocolConfig(pc -> pc.priorKnowledge(true))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> upgradeClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> tlsClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .baseUri("https://localhost:" + tlsPort + "/versionspecific")
            .tls(Tls.builder()
                    .trust(trust -> trust
                            .keystore(store -> store
                                    .passphrase("password")
                                    .trustStore(true)
                                    .keystore(Resource.create("client.p12"))))
                         .build())
            .build();

    Http2WebClientTest(WebServer server) {
        plainPort = server.port();
        tlsPort = server.port("https");
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        executorService = Executors.newFixedThreadPool(10);

        Keys privateKeyConfig =
                Keys.builder()
                        .keystore(keystore -> keystore.keystore(Resource.create("server.p12"))
                                .passphrase("password"))
                        .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        HttpRouting.Builder router = HttpRouting.builder()
                .get("/", (req, res) -> res.send("Hello world!"))
                .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                .route(Http2Route.route(GET, "/versionspecific", (req, res) -> {
                    res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).get());
                    res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().get("custQueryParam"));
                    res.send("HTTP/2 route");
                }))
                .route(Http2Route.route(PUT, "/versionspecific", (req, res) -> {
                    res.header(SERVER_CUSTOM_HEADER_NAME, req.headers().get(CLIENT_CUSTOM_HEADER_NAME).get());
                    res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).get());
                    res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().get("custQueryParam"));
                    res.send("PUT " + req.content().as(String.class));
                }))
                .route(Http2Route.route(POST, "/versionspecific", (req, res) -> {
                    res.header(SERVER_CUSTOM_HEADER_NAME, req.headers().get(CLIENT_CUSTOM_HEADER_NAME).get());
                    res.header(CLIENT_USER_AGENT_HEADER_NAME, req.headers().get(USER_AGENT).get());
                    res.header(SERVER_HEADER_FROM_PARAM_NAME, req.query().get("custQueryParam"));
                    res.send("POST " + req.content().as(String.class));
                }))
                .route(Http2Route.route(GET, "/versionspecific/h2streaming", (req, res) -> {
                           res.status(Status.OK_200);
                           String execId = req.query().get("execId");
                           try (OutputStream os = res.outputStream()) {
                               for (int i = 0; i < 5; i++) {
                                   os.write(String.format(execId + "BAF%03d", i).getBytes());
                                   Thread.sleep(10);
                               }
                           } catch (IOException | InterruptedException e) {
                               throw new RuntimeException(e);
                           }
                       }));

        serverBuilder
                .port(-1)
                .host("localhost")
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.builder()
                                                                    .initialWindowSize(10)
                                                                    .build())
                                               .build())
                .putSocket("https", builder -> builder.port(-1)
                        .host("localhost")
                        .tls(tls)
                        .receiveBufferSize(4096)
                        .backlog(8192)
                )
                .routing(router)
                // we want the same routing on the other socket
                .routing("https", router.copy());
    }

    static Stream<Arguments> clientTypes() {
        return Stream.of(
                Arguments.of("localConnectionCache", LazyValue.create(() -> localCacheClient.get())),
                Arguments.of("globalConnectionCache", globalCacheClient),
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
    void clientGet(String name, Supplier<Http2Client> client) {
        try (Http2ClientResponse response = client.get()
                .get()
                .queryParam("custQueryParam", "test-get")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-get"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPut(String clientType, Supplier<Http2Client> client) {
        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(PUT)
                .queryParam("custQueryParam", "test-put")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("PUT " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).get(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-put"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPost(String clientType, Supplier<Http2Client> client) {
        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(POST)
                .queryParam("custQueryParam", "test-post")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("POST " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).get(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-post"));
        }
    }

//    @Disabled("Failing intermittently, to be investigated")
    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void multiplexParallelStreamsGet(String clientType, Supplier<Http2Client> client)
            throws ExecutionException, InterruptedException, TimeoutException {

        Http2Client http2Client = client.get();
        Consumer<Integer> callable = id -> {
            try (Http2ClientResponse response = http2Client
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
