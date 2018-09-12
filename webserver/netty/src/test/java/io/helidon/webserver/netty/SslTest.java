/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.Builder;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SSLContextBuilder;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.LoggingTestUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test of SSL Netty layer.
 */
public class SslTest {

    private static final Logger LOGGER = Logger.getLogger(SslTest.class.getName());

    private static WebServer webServer;
    private static Client client;

    /**
     * Start the secured Web Server
     *
     * @param port the port on which to start the secured server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        Builder<SSLContext> nettyContext = SSLContextBuilder.create(KeyConfig.pemBuilder()
                                                                             .key(Resource.from("ssl/key.pkcs8.pem"))
                                                                             .certChain(Resource.from("ssl/certificate.pem"))
                                                                             .build());

        webServer = WebServer.create(
                ServerConfiguration.builder()
                                   .ssl(nettyContext)
                                   .port(port),
                Routing.builder()
                       .any((req, res) -> res.send("It works!")))
                             .start()
                             .toCompletableFuture()
                             .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started secured server at: https://localhost:" + webServer.port());
    }

    /**
     * Java main method to start the secured server at port {@code 8443}.
     * <p>
     * Once started, run either
     * <pre><code>
     *     curl https://localhost:8443/ -vvv --insecure
     * </code></pre>
     * or open the {@code https://localhost:8443} in your browser.
     * <p>
     * Note that the certificate is self-signed.
     *
     * @param args not used
     * @throws Exception in case of an error
     */
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(LoggingTestUtils.class.getResourceAsStream("/logging-test.properties"));

        startServer(8443);
    }

    @Test
    public void testSecuredServerWithJerseyClient() throws Exception {

        Response response = client.target("https://localhost:" + webServer.port())
                                  .request()
                                  .get();

        doAssert(response);
    }

    @Test
    public void multipleSslRequestsKeepAlive() throws Exception {
        WebTarget target = client.target("https://localhost:" + webServer.port());
        doAssert(target.request().get());
        doAssert(target.request().get());
    }

    @Test
    public void multipleSslRequestsNonKeepAlive() throws Exception {
        WebTarget target = client.target("https://localhost:" + webServer.port());
        // send an entity that won't be consumed, as such a new connection will be created by the server
        doAssert(target.request().post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE)));
        doAssert(target.request().post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE)));
    }

    private void doAssert(Response response) {
        assertEquals("It works!", response.readEntity(String.class),
                "Unexpected content; returned status code: " + response.getStatus());
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
    }

    @BeforeAll
    public static void createClientAcceptingAllCertificates() throws Exception {
        SSLContext sc = clientSslContextTrustAll();

        client = ClientBuilder.newBuilder()
                              .sslContext(sc)
                              .hostnameVerifier((s, sslSession) -> true)
                              .build();
    }

    public static SSLContext clientSslContextTrustAll() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);
        }
        if (client != null) {
            client.close();
        }
    }
}
