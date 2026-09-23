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

package io.helidon.webclient.http3;

import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.Proxy;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http3.Http3Config;

final class Http3BenchmarkEnvironment implements AutoCloseable {
    private static final String SERVER_KEYSTORE_RESOURCE =
            "/io/helidon/quic/benchmark/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE_RESOURCE =
            "/io/helidon/quic/benchmark/client-truststore.p12";
    private static final String SERVER_KEY_ALIAS = "server";
    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    private final InetAddress address;
    private final WebServer server;
    private final Tls clientTls;

    private Http3BenchmarkEnvironment(InetAddress address, WebServer server, Tls clientTls) {
        this.address = address;
        this.server = server;
        this.clientTls = clientTls;
    }

    static Http3BenchmarkEnvironment create(Consumer<HttpRouting.Builder> routing) throws Exception {
        KeyStore serverKeyStore = loadKeyStore(SERVER_KEYSTORE_RESOURCE);
        if (!(serverKeyStore.getKey(SERVER_KEY_ALIAS, STORE_PASSWORD) instanceof PrivateKey privateKey)) {
            throw new IllegalStateException("HTTP/3 benchmark server key is not a private key");
        }
        Certificate[] certificateChain = serverKeyStore.getCertificateChain(SERVER_KEY_ALIAS);
        if (certificateChain == null || certificateChain.length == 0) {
            throw new IllegalStateException("HTTP/3 benchmark server certificate chain is empty");
        }
        List<X509Certificate> x509CertificateChain = new ArrayList<>(certificateChain.length);
        for (Certificate certificate : certificateChain) {
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new IllegalStateException("HTTP/3 benchmark server certificate is not X.509");
            }
            x509CertificateChain.add(x509Certificate);
        }

        KeyStore trustStore = loadKeyStore(CLIENT_TRUSTSTORE_RESOURCE);
        List<X509Certificate> trustCertificates = new ArrayList<>();
        Enumeration<String> aliases = trustStore.aliases();
        while (aliases.hasMoreElements()) {
            Certificate certificate = trustStore.getCertificate(aliases.nextElement());
            if (certificate instanceof X509Certificate x509Certificate) {
                trustCertificates.add(x509Certificate);
            }
        }
        if (trustCertificates.isEmpty()) {
            throw new IllegalStateException("HTTP/3 benchmark trust store is empty");
        }

        Tls serverTls = Tls.builder()
                .privateKey(privateKey)
                .privateKeyCertChain(x509CertificateChain)
                .build();
        Tls clientTls = Tls.builder()
                .trust(trust -> trust.certs(trustCertificates))
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
        InetAddress address = InetAddress.getLoopbackAddress();
        WebServer server = WebServer.builder()
                .address(address)
                .port(0)
                .protocolsDiscoverServices(false)
                .tls(serverTls)
                .addProtocol(Http3Config.create())
                .routing(routing)
                .build();
        try {
            server.start();
            return new Http3BenchmarkEnvironment(address, server, clientTls);
        } catch (Exception | Error failure) {
            try {
                server.stop();
            } catch (Exception | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    InetAddress address() {
        return address;
    }

    int port() {
        return server.port();
    }

    String baseUri(int port) {
        return "https://localhost:" + port;
    }

    Http3Client client(String baseUri, Duration handshakeTimeout) {
        return Http3Client.builder()
                .baseUri(baseUri)
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .dnsResolver((_, _) -> address)
                .tls(clientTls)
                .protocolConfig(Http3ClientProtocolConfig.builder()
                                        .priorKnowledge(true)
                                        .initialResponseTimeout(handshakeTimeout)
                                        .handshakeTimeout(handshakeTimeout)
                                        .build())
                .build();
    }

    @Override
    public void close() {
        server.stop();
    }

    private static KeyStore loadKeyStore(String resource) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Http3BenchmarkEnvironment.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing HTTP/3 benchmark key store: " + resource);
            }
            keyStore.load(input, STORE_PASSWORD);
        }
        return keyStore;
    }
}
