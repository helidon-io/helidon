/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests.http2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class Http2ClientTest {
    private static final String MESSAGE = "Hello World!";
    private static final String TEST_HEADER_NAME = "custom_header";
    private static final String TEST_HEADER_VALUE = "as!fd";
    private static final Header TEST_HEADER = HeaderValues.create(HeaderNames.create(TEST_HEADER_NAME), TEST_HEADER_VALUE);
    private final Http1Client http1Client;
    private final Supplier<Http2Client> tlsClient;
    private final Supplier<Http2Client> plainClient;
    private final Tls clientTls;
    private final int tlsPort;
    private final int sniTlsPort;
    private final int plainPort;

    Http2ClientTest(WebServer server, Http1Client http1Client) {
        plainPort = server.port();
        tlsPort = server.port("https");
        sniTlsPort = server.port("https-sni");
        this.http1Client = http1Client;
        this.clientTls = clientTls();
        this.tlsClient = () -> Http2Client.builder()
                .baseUri("https://localhost:" + tlsPort + "/")
                .shareConnectionCache(false)
                .tls(clientTls)
                .build();
        this.plainClient = () -> Http2Client.builder()
                .baseUri("http://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
                .build();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .build();

        SSLParameters sniParameters = new SSLParameters();
        sniParameters.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("(first|second|host-header)\\.example")));
        Tls sniTls = Tls.builder()
                .privateKey(privateKeyConfig)
                .privateKeyCertChain(privateKeyConfig)
                .sslParameters(sniParameters)
                .build();

        serverBuilder.putSocket("https",
                                socketBuilder -> socketBuilder.tls(tls));
        serverBuilder.putSocket("https-sni",
                                socketBuilder -> socketBuilder.tls(sniTls));
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                .send(MESSAGE)));
    }

    @SetUpRoute("https")
    static void routerHttps(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                .send(MESSAGE)));
    }

    @SetUpRoute("https-sni")
    static void routerHttpsSni(HttpRouting.Builder router) {
        // explicitly on HTTP/2 only, to make sure we do upgrade
        router.route(Http2Route.route(Method.GET, "/", (req, res) -> res.header(TEST_HEADER)
                        .send(MESSAGE)))
                .route(Http2Route.route(Method.GET, "/socket-id", (req, res) -> res.send(req.socketId())));
    }

    @Test
    void testHttp1() {
        // make sure the HTTP/1 route is not working
        try (Http1ClientResponse response = http1Client
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
        }
    }

    @Test
    void testRequestHeadersUpdated() {
        var client = WebClient.builder()
                .baseUri("http://localhost:" + plainPort + "/")
                .addProtocolConfig(Http2ClientProtocolConfig.builder()
                                           .priorKnowledge(true)
                                           .build())
                .build();

        HttpClientRequest request = client.get()
                .protocolId("h2");

        request.request(String.class);

        // this header is computed by Helidon, and would not be present unless the bug 10175 was fixed
        assertThat(request.headers().contentLength(), is(OptionalLong.of(0)));

        client.closeResource();
    }

    @Test
    void testSchemeValidation() {
        try (var r = Http2Client.builder()
                .baseUri("test://localhost:" + plainPort + "/")
                .shareConnectionCache(false)
                .build()
                .get("/")
                .request()) {

            fail("Should have failed because of invalid scheme.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith("Not supported scheme test"));
        }
    }

    @Test
    void testUpgrade() {
        try (Http2ClientResponse response = plainClient.get()
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }

    @Test
    void testAppProtocol() {
        try (Http2ClientResponse response = tlsClient.get()
                .get("/")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }

    @Test
    void testGenericHostHeaderSniNegotiatesHttp2OverTls() {
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + tlsPort + "/")
                .tls(clientTls)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .build();
        try (HttpClientResponse response = client.get()
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testGenericHostHeaderSniUsesHostHeaderForTlsHandshake() {
        Tls tlsWithoutEndpointIdentification = clientTlsWithoutEndpointIdentification();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + sniTlsPort + "/")
                .tls(tlsWithoutEndpointIdentification)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .build();
        try (HttpClientResponse response = client.get()
                .header(HeaderValues.create(HeaderNames.HOST, "host-header.example:" + sniTlsPort))
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testGenericHostHeaderSniUsesServiceHostHeaderForTlsHandshake() {
        Tls tlsWithoutEndpointIdentification = clientTlsWithoutEndpointIdentification();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + sniTlsPort + "/")
                .tls(tlsWithoutEndpointIdentification)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .addService((chain, request) -> {
                    request.headers().set(HeaderValues.create(HeaderNames.HOST, "host-header.example:" + sniTlsPort));
                    return chain.proceed(request);
                })
                .build();
        try (HttpClientResponse response = client.get()
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testGenericHostHeaderSniSeparatesHttp2ConnectionCache() {
        Tls tlsWithoutEndpointIdentification = clientTlsWithoutEndpointIdentification();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + sniTlsPort + "/")
                .tls(tlsWithoutEndpointIdentification)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .build();
        try {
            String firstSocketId = client.get()
                    .path("/socket-id")
                    .header(HeaderValues.create(HeaderNames.HOST, "first.example:" + sniTlsPort))
                    .requestEntity(String.class);
            String secondSocketId = client.get()
                    .path("/socket-id")
                    .header(HeaderValues.create(HeaderNames.HOST, "second.example:" + sniTlsPort))
                    .requestEntity(String.class);

            assertThat(secondSocketId.equals(firstSocketId), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testGenericHostHeaderSniFallsBackToHttp1WhenTlsDoesNotNegotiateAlpn() throws Exception {
        NoAlpnHttp1TlsServer server = NoAlpnHttp1TlsServer.start();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + server.port() + "/")
                .tls(clientTls)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .build();
        try (HttpClientResponse response = client.get()
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("http1"));

            List<String> requestLines = server.requestLines();
            assertThat(requestLines.getFirst(), is("GET / HTTP/1.1"));
            assertThat(hasHeader(requestLines, HeaderNames.UPGRADE.defaultCase()), is(false));
            assertThat(hasHeader(requestLines, "HTTP2-Settings"), is(false));
        } finally {
            client.closeResource();
            server.close();
        }
    }

    @Test
    void testGenericHostHeaderSniHonorsH2OnlyProtocolPreferenceWhenTlsDoesNotNegotiateAlpn() throws Exception {
        NoAlpnHttp1TlsServer server = NoAlpnHttp1TlsServer.start();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + server.port() + "/")
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID))
                .tls(clientTls)
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .build();
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> client.get().request());

            assertThat(e.getMessage(), startsWith("Cannot handle request"));
        } finally {
            client.closeResource();
            server.close();
        }
    }

    @Test
    void testPriorKnowledge() {
        try (Http2ClientResponse response = tlsClient.get()
                .get("/")
                .priorKnowledge(true)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(MESSAGE));
            assertThat(TEST_HEADER + " header must be present in response",
                       response.headers().contains(TEST_HEADER), is(true));
        }
    }

    private static boolean hasHeader(List<String> requestLines, String headerName) {
        String prefix = headerName.toLowerCase(Locale.ROOT) + ":";
        return requestLines.stream()
                .map(it -> it.toLowerCase(Locale.ROOT))
                .anyMatch(it -> it.startsWith(prefix));
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static Tls clientTlsWithoutEndpointIdentification() {
        return Tls.builder()
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static final class NoAlpnHttp1TlsServer implements AutoCloseable {
        private static final char[] PASSWORD = "password".toCharArray();

        private final SSLServerSocket serverSocket;
        private final CompletableFuture<List<String>> requestLines = new CompletableFuture<>();

        private NoAlpnHttp1TlsServer(SSLServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            Thread.ofVirtual().start(this::serve);
        }

        static NoAlpnHttp1TlsServer start() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Http2ClientTest.class.getClassLoader().getResourceAsStream("server.p12")) {
                store.load(Objects.requireNonNull(input, "server.p12"), PASSWORD);
            }
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(store, PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), null, null);
            SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0);
            return new NoAlpnHttp1TlsServer(socket);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<String> requestLines() throws Exception {
            return requestLines.get(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
        }

        private void serve() {
            try {
                while (!serverSocket.isClosed()) {
                    try (SSLSocket socket = (SSLSocket) serverSocket.accept();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                                                             StandardCharsets.US_ASCII));
                            Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)) {

                        socket.startHandshake();
                        List<String> lines = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            lines.add(line);
                        }
                        if (lines.isEmpty()) {
                            continue;
                        }
                        requestLines.complete(List.copyOf(lines));
                        writer.write("HTTP/1.1 200 OK\r\n"
                                             + "Content-Length: 5\r\n"
                                             + "Connection: close\r\n"
                                             + "\r\n"
                                             + "http1");
                        writer.flush();
                        return;
                    }
                }
            } catch (Throwable t) {
                requestLines.completeExceptionally(t);
            }
        }
    }
}
