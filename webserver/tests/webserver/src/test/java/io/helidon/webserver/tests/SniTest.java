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

package io.helidon.webserver.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.SniSelectionPolicy;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniTest {
    private static final String SNI_HOST = "api.example.com";
    private static final String OTHER_HOST = "admin.example.com";
    private static final String DIFFERENT_HOST = "other.example.com";
    private static final String DEFAULT_CIPHER = "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";
    private static final String VIRTUAL_HOST_CIPHER = "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256";

    private static WebServer server;
    private static Http1Client defaultTlsClient;
    private static Http1Client virtualHostTlsClient;

    @BeforeAll
    static void startServer() {
        Tls defaultTls = tls(DEFAULT_CIPHER);
        Tls virtualHostTls = tls(VIRTUAL_HOST_CIPHER);

        server = WebServerConfig.builder()
                .host("localhost")
                .port(0)
                .shutdownHook(false)
                .tls(defaultTls)
                .addConnectionSelector(http1WithoutHeaderValidation())
                .addVirtualHost(virtualHost -> virtualHost
                        .host(SNI_HOST)
                        .tls(virtualHostTls))
                .routing(routing -> routing
                        .get("/", (req, res) -> res.send("ok"))
                        .get("/sni", (req, res) -> res.send(sniHosts(req))))
                .build()
                .start();

        URI defaultUri = URI.create("https://localhost:" + server.port() + "/");
        defaultTlsClient = client(defaultUri, DEFAULT_CIPHER);
        virtualHostTlsClient = client(defaultUri, VIRTUAL_HOST_CIPHER);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void rejectsMissingHostHeader() throws Exception {
        try (SSLSocket socket = tlsSocket(SNI_HOST, VIRTUAL_HOST_CIPHER)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();

            OutputStream output = socket.getOutputStream();
            output.write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine(), startsWith("HTTP/1.1 400 "));
        }
    }

    @Test
    void rejectsInvalidHostHeader() throws Exception {
        try (SSLSocket socket = tlsSocket(SNI_HOST, VIRTUAL_HOST_CIPHER)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();

            OutputStream output = socket.getOutputStream();
            output.write("GET / HTTP/1.1\r\nHost: example.com:abc\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine(), startsWith("HTTP/1.1 400 "));
        }
    }

    @Test
    void rejectsMultipleHostHeaders() throws Exception {
        try (SSLSocket socket = tlsSocket(SNI_HOST, VIRTUAL_HOST_CIPHER)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();

            OutputStream output = socket.getOutputStream();
            output.write(("GET / HTTP/1.1\r\n"
                                  + "Host: " + SNI_HOST + "\r\n"
                                  + "Host: " + OTHER_HOST + "\r\n"
                                  + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine(), startsWith("HTTP/1.1 400 "));
        }
    }

    @Test
    void selectsVirtualHostTlsFromSni() {
        String entity = virtualHostTlsClient.get()
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .requestEntity(String.class);

        assertThat(entity, is("ok"));
    }

    @Test
    void exposesSniHosts() {
        String entity = virtualHostTlsClient.get("/sni")
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .requestEntity(String.class);

        assertThat(entity, is(SNI_HOST + "|" + SNI_HOST));
    }

    @Test
    void rejectsAuthorityMismatch() {
        try (Http1ClientResponse response = virtualHostTlsClient.get()
                .header(HeaderValues.create(HeaderNames.HOST, OTHER_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(SNI_HOST))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsUnmatchedSniAuthorityMismatch() {
        try (Http1ClientResponse response = defaultTlsClient.get()
                .header(HeaderValues.create(HeaderNames.HOST, DIFFERENT_HOST))
                .sni(sni -> sni.mode(SniMode.EXPLICIT)
                        .host(OTHER_HOST))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsFallbackAuthorityForConfiguredVirtualHost() {
        try (Http1ClientResponse response = defaultTlsClient.get()
                .header(HeaderValues.create(HeaderNames.HOST, SNI_HOST))
                .sni(sni -> sni.mode(SniMode.DISABLED))
                .request()) {

            assertThat(response.status(), is(Status.MISDIRECTED_REQUEST_421));
        }
    }

    @Test
    void rejectsUnmatchedSniDuringTlsHandshake() throws Exception {
        WebServer rejectingServer = rejectingServer(SniSelectionPolicy.FALLBACK, SniSelectionPolicy.REJECT);
        try {
            try (SSLSocket socket = tlsSocket(rejectingServer.port(), OTHER_HOST, DEFAULT_CIPHER)) {
                socket.setSoTimeout(5000);

                assertThrows(IOException.class, socket::startHandshake);
            }
        } finally {
            rejectingServer.stop();
        }
    }

    @Test
    void rejectsMissingSniDuringTlsHandshake() throws Exception {
        WebServer rejectingServer = rejectingServer(SniSelectionPolicy.REJECT, SniSelectionPolicy.FALLBACK);
        try {
            try (SSLSocket socket = tlsSocketWithoutSni(rejectingServer.port(), DEFAULT_CIPHER)) {
                socket.setSoTimeout(5000);

                assertThrows(IOException.class, socket::startHandshake);
            }
        } finally {
            rejectingServer.stop();
        }
    }

    private static WebServer rejectingServer(SniSelectionPolicy missing, SniSelectionPolicy unmatched) {
        Tls tls = tls(DEFAULT_CIPHER);
        return WebServerConfig.builder()
                .host("localhost")
                .port(0)
                .shutdownHook(false)
                .tls(tls)
                .sni(sni -> sni.missing(missing)
                        .unmatched(unmatched))
                .addConnectionSelector(http1WithoutHeaderValidation())
                .addVirtualHost(virtualHost -> virtualHost
                        .host(SNI_HOST)
                        .tls(tls))
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();
    }

    private static Http1Client client(URI defaultUri, String cipher) {
        return Http1Client.builder()
                .baseUri(defaultUri)
                .tls(tls -> tls.enabledCipherSuites(List.of(cipher))
                        .trustAll(true)
                        .endpointIdentificationAlgorithm("NONE"))
                .build();
    }

    private static ServerConnectionSelector http1WithoutHeaderValidation() {
        return Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .validateRequestHeaders(false)
                                .build())
                .build();
    }

    private static SSLSocket tlsSocket(String sniHost, String cipher) throws Exception {
        return tlsSocket(server.port(), sniHost, cipher);
    }

    private static SSLSocket tlsSocket(int port, String sniHost, String cipher) throws Exception {
        SSLSocket socket = (SSLSocket) trustAllContext().getSocketFactory().createSocket("localhost", port);
        socket.setEnabledCipherSuites(new String[] {cipher});
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setServerNames(List.of(new SNIHostName(sniHost)));
        socket.setSSLParameters(parameters);
        return socket;
    }

    private static SSLSocket tlsSocketWithoutSni(int port, String cipher) throws Exception {
        SSLSocket socket = (SSLSocket) trustAllContext().getSocketFactory().createSocket("localhost", port);
        socket.setEnabledCipherSuites(new String[] {cipher});
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setServerNames(List.of());
        socket.setSSLParameters(parameters);
        return socket;
    }

    private static SSLContext trustAllContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {TRUST_ALL}, new SecureRandom());
        return sslContext;
    }

    private static String sniHosts(ServerRequest request) {
        return request.sniRequestedHost().orElse("") + "|" + request.sniMatchedHost().orElse("");
    }

    private static Tls tls(String cipher) {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase("helidon")
                        .keystore(Resource.create("certificate.p12")))
                .build();
        return Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .enabledCipherSuites(List.of(cipher))
                .build();
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
