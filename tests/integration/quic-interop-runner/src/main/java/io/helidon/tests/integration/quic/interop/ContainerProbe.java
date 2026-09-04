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
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.Api;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/**
 * HTTP/3 probe for validating the packaged interop endpoint from inside its container.
 */
@Api.Internal
public final class ContainerProbe {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private ContainerProbe() {
    }

    /**
     * Fetch a URI over HTTP/3 using the supplied PEM certificate as the only trust anchor.
     *
     * @param args the URI and PEM certificate path
     * @throws Exception if the request or certificate setup fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected URI and PEM certificate path");
        }

        X509Certificate certificate;
        try (InputStream input = Files.newInputStream(Path.of(args[1]))) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("interop-server", certificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

        HttpClient client = HttpClient.newBuilder()
                .version(HTTP_3)
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.of(null))
                .sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(args[0]))
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.version() != HTTP_3) {
            throw new IllegalStateException("Unexpected response: " + response.statusCode() + " " + response.version());
        }
        System.out.print(response.body());
    }
}
