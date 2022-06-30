/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientTls;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test of SSL Netty layer with MTLS enabled.
 */
public class MtlsTest {

    private static final Logger LOGGER = Logger.getLogger(MtlsTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    private static WebServer webServer;
    private static WebClient clientWithoutCertificate;
    private static WebClient clientWithCertificate;

    /**
     * Start the secured Web Server
     *
     * @param port the port on which to start the secured server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        HashMap<String, String> rawConfig = new HashMap<>();
        rawConfig.put("client-auth", "REQUIRE");
        rawConfig.put("trust.pem.certificates.resource.resource-path", "ssl/certificate.pem");
        rawConfig.put("private-key.pem.key.resource.resource-path", "ssl/key.pkcs8.pem");
        rawConfig.put("private-key.pem.cert-chain.resource.resource-path", "ssl/certificate.pem");

        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .port(port)
                        .tls(WebServerTls.builder()
                                .config(Config.create(MapConfigSource.create(rawConfig)))
                                .build())
                )
                .routing(r -> r
                        .any((req, res) -> {
                            // This is annoyingly complex to pull just the CN out of an x509 cert, but it's generally easier if the caller
                            // has access to other libraries like bouncy castle.
                            Optional<X509Certificate> cert = req.context().get(WebServerTls.CLIENT_X509_CERTIFICATE, X509Certificate.class);
                            res.send(cert.map(X509Certificate::getSubjectX500Principal).map(X500Principal::getName)
                                    .map(name -> Pattern.compile("(?:^|,\s?)(?:CN=(?<val>\"(?:[^\"]|\"\")+\"|[^,]+))").matcher(name))
                                    .map(matcher -> matcher.find() ? matcher.group(1) : "no match")
                                    .orElse("unknown"));
                        })
                )
                .build()
                .start()
                .await(TIMEOUT);

        LOGGER.info("Started secured server at: https://localhost:" + webServer.port());
    }

    @Test
    public void testNoClientCert() {
        Throwable exc = assertThrows(CompletionException.class, () -> clientWithoutCertificate.get()
                .uri("https://localhost:" + webServer.port())
                .request(String.class)
                .await(TIMEOUT)
        );
        assertThat(exc.getCause(), instanceOf(DecoderException.class));
        assertThat(exc.getCause().getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(exc.getCause().getCause().getMessage(), is("Received fatal alert: bad_certificate"));
    }

    @Test
    public void testWithClientCert() throws Exception {
        clientWithCertificate.get()
            .uri("https://localhost:" + webServer.port())
            .request(String.class)
            .thenAccept(it -> assertThat(it, is("helidon-webserver-netty-test")))
            .await(TIMEOUT);
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
    }

    @BeforeAll
    public static void setup() {
        clientWithoutCertificate = WebClient.builder()
            .tls(WebClientTls.builder()
                .trustAll(true)
                .build())
            .build();
        clientWithCertificate = WebClient.builder()
            .tls(WebClientTls.builder()
                // the certificate is self-signed so we can use it for TLS, but its CN obviously doesn't match 'localhost'
                .disableHostnameVerification(true)
                .certificateTrustStore(KeyConfig.pemBuilder()
                    .certificates(Resource.create("ssl/certificate.pem"))
                    .build())
                .clientKeyStore(KeyConfig.pemBuilder()
                    .key(Resource.create("ssl/key.pkcs8.pem"))
                    .certChain(Resource.create("ssl/certificate.pem"))
                    .build())
                .build())
            .build();
    }

    @AfterAll
    public static void teardown() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }
}
