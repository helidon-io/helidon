/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.integration.webserver.upgrade.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.integration.webserver.upgrade.Main;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.testing.junit5.webserver.ServerTest;

import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.common.http.Http.Method.GET;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ServerTest
class UpgradeCodecsCompositionTest {

    private static WebServer webServer;
    private static WebServer webServerTls;
    private static HttpClient httpClient;
    private static Http1Client webClient;

    @BeforeAll
    static void beforeAll() {
        webServer = Main.startServer(false);
        webServerTls = Main.startServer(true);
        httpClient = HttpClient.newBuilder().sslContext(insecureContext()).build();
        webClient = Http1Client.builder()
                .tls(Tls.builder()
                        .sslContext(insecureContext())
                        .trust(trust -> trust
                                .keystore(store -> store
                                        .passphrase("password")
                                        .keystore(Resource.create("server.p12"))))
                        .build())
                .build();
    }

    @AfterAll
    static void afterAll() {
        webServer.stop();
        webServerTls.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ws://localhost:%d/ws-conf/echo",
            "wss://localhost:%d/ws-conf/echo",
            "ws://localhost:%d/ws-annotated/echo",
            "wss://localhost:%d/ws-annotated/echo"
    })
    void testWsEcho(String context) throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch countDownLatch = new CountDownLatch(2);
        WebSocket ws = HttpClient
                .newBuilder()
                .sslContext(insecureContext())
                .build()
                .newWebSocketBuilder()
                .buildAsync(resolveUri(context), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(Long.MAX_VALUE);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        received.add(String.valueOf(data));
                        countDownLatch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();
        ws.sendText("I am waiting here!", true);
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        assertThat(received, Matchers.contains("Hello this is server calling on open!", "I am waiting here!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/",
            "https://localhost:%d/",
    })
    void genericHttp20(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_2).body(), is("HTTP Version V2_0\n"));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V2_0)) {
            assertThat(response.entity().as(String.class), is("HTTP Version V2_0\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/",
            "https://localhost:%d/",
    })
    void genericHttp11(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_1_1).body(), is("HTTP Version V1_1\n"));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V1_1)) {
            assertThat(response.entity().as(String.class), is("HTTP Version V1_1\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/versionspecific",
            "https://localhost:%d/versionspecific",
    })
    void versionSpecificHttp11(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_1_1).body(), is("HTTP/1.1 route\n"));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V1_1)) {
            assertThat(response.entity().as(String.class), is("HTTP/1.1 route\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/versionspecific",
            "https://localhost:%d/versionspecific",
    })
    void versionSpecificHttp20(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_2).body(), is("HTTP/2.0 route\n"));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V2_0)) {
            assertThat(response.entity().as(String.class), is("HTTP/2.0 route\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/versionspecific1",
            "https://localhost:%d/versionspecific1",
    })
    void versionSpecificHttp11Negative(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_2).statusCode(), is(404));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V2_0)) {
            assertThat(response.status().code(), is(404));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:%d/versionspecific2",
            "https://localhost:%d/versionspecific2",
    })
    void versionSpecificHttp20Negative(String url) throws IOException, InterruptedException {
        assertThat(httpClient(GET, url, HTTP_1_1).statusCode(), is(404));
        try (Http1ClientResponse response = webClient(GET, url, Http.Version.V1_1)) {
            assertThat(response.status().code(), is(404));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HTTP/1.1 GET http://localhost:%d/multi-something",
            "HTTP/1.1 PUT https://localhost:%d/multi-something",
            "HTTP/1.1 POST https://localhost:%d/multi-something",
            "HTTP/2.0 GET http://localhost:%d/multi-something",
            "HTTP/2.0 PUT https://localhost:%d/multi-something",
            "HTTP/2.0 POST https://localhost:%d/multi-something",
    })
    void versionSpecificHttp20MultipleMethods(String param) throws IOException, InterruptedException {
        String[] split = param.split("\s");
        String version = split[0];
        String method = split[1];
        String url = split[2];

        String expectedResponse = version + " route " + method + "\n";

        assertThat(httpClient(Http.Method.create(method), url, version.contains("2") ? HTTP_2 : HTTP_1_1).body(), is(expectedResponse));
        try (Http1ClientResponse response = webClient(Http.Method.create(method), url, Http.Version.create(version))) {
            assertThat(response.entity().as(String.class), is(expectedResponse));
        }
    }


    private HttpResponse<String> httpClient(Http.Method method,
                                            String url,
                                            HttpClient.Version version) throws IOException, InterruptedException {
        return httpClient.send(HttpRequest.newBuilder()
                .version(version)
                .uri(resolveUri(url))
                .method(method.text(), HttpRequest.BodyPublishers.ofString("test"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private Http1ClientResponse webClient(Http.Method method, String url, Http.Version version) {
        return webClient.method(method)
                .uri(resolveUri(url))
//                        .httpVersion(version)
                .request();
    }

    static SSLContext insecureContext() {
        TrustManager[] noopTrustManager = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String string) {
                    }

                    public void checkServerTrusted(X509Certificate[] xcs, String string) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("ssl");
            sc.init(null, noopTrustManager, null);
            return sc;
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private URI resolveUri(String mask) {
        if (mask.startsWith("https://") || mask.startsWith("wss://")) {
            return URI.create(String.format(mask, webServerTls.port()));
        } else {
            return URI.create(String.format(mask, webServer.port()));
        }
    }

}
