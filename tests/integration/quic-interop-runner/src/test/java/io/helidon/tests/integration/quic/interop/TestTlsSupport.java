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

package io.helidon.tests.integration.quic.interop;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

final class TestTlsSupport {
    static final char[] PASSWORD = "password".toCharArray();
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Path HTTP3_SERVER_KEYSTORE = sharedHttp3TestResource("server.p12");
    private static final Path HTTP3_CLIENT_TRUSTSTORE = sharedHttp3TestResource("client.p12");

    private TestTlsSupport() {
    }

    static SSLContext clientSslContext() throws Exception {
        KeyStore trustStore = loadStore(HTTP3_CLIENT_TRUSTSTORE);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return clientContext;
    }

    static void exportServerPem(Path certChain, Path privateKey) throws Exception {
        KeyStore keyStore = loadStore(HTTP3_SERVER_KEYSTORE);
        String alias = keyStore.aliases().nextElement();
        Key key = keyStore.getKey(alias, PASSWORD);
        Certificate[] chain = keyStore.getCertificateChain(alias);

        StringBuilder certChainContent = new StringBuilder();
        for (Certificate certificate : chain) {
            appendPem(certChainContent, "CERTIFICATE", certificate.getEncoded());
        }
        Files.writeString(certChain, certChainContent, StandardCharsets.US_ASCII);

        StringBuilder privateKeyContent = new StringBuilder();
        appendPem(privateKeyContent, "PRIVATE KEY", key.getEncoded());
        Files.writeString(privateKey, privateKeyContent, StandardCharsets.US_ASCII);
    }

    private static void appendPem(StringBuilder builder, String label, byte[] bytes) {
        builder.append("-----BEGIN ")
                .append(label)
                .append("-----\n");
        builder.append(Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(bytes));
        builder.append('\n')
                .append("-----END ")
                .append(label)
                .append("-----\n");
    }

    private static Path sharedHttp3TestResource(String resourceName) {
        Path current = Path.of("").toAbsolutePath().normalize();

        for (Path candidateRoot = current; candidateRoot != null; candidateRoot = candidateRoot.getParent()) {
            Path candidate = candidateRoot.resolve("webserver")
                    .resolve("tests")
                    .resolve("http3")
                    .resolve("src")
                    .resolve("test")
                    .resolve("resources")
                    .resolve(resourceName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Cannot locate shared HTTP/3 test resource: " + resourceName
                                                + " from " + current);
    }

    private static KeyStore loadStore(Path path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(path)) {
            store.load(stream, PASSWORD);
        }
        return store;
    }
}
