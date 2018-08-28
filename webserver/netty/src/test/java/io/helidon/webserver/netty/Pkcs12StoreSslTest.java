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

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SSLContextBuilder;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Pkcs12StoreSslTest.
 */
public class Pkcs12StoreSslTest {

    private static Client client;

    @BeforeAll
    public static void createClientAcceptingAllCertificates() throws Exception {
        SSLContext sc = SslTest.clientSslContextTrustAll();

        client = ClientBuilder.newBuilder()
                              .sslContext(sc)
                              .hostnameVerifier((s, sslSession) -> true)
                              .build();
    }

    @Test
    public void testPkcs12() throws Exception {
        SSLContext sslContext = SSLContextBuilder.create(KeyConfig.keystoreBuilder()
                                                                  .keystore(Resource.from("ssl/certificate.p12"))
                                                                  .keystorePassphrase(new char[] {'h', 'e', 'l', 'i', 'd', 'o', 'n'})
                                                                  .build())
                                                 .build();

        WebServer otherWebServer =
                WebServer.create(ServerConfiguration.builder()
                                                    .ssl(sslContext),
                                 Routing.builder()
                                        .any((req, res) -> res.send("It works!"))
                                        .build())
                         .start()
                         .toCompletableFuture()
                         .get(10, TimeUnit.SECONDS);

        otherWebServer.start()
                      .toCompletableFuture()
                      .join();
        try {
            WebTarget target = client.target("https://localhost:" + otherWebServer.port());
            Response response = target.request().get();
            assertEquals("It works!", response.readEntity(String.class),
                    "Unexpected content; returned status code: " + response.getStatus());
        } finally {
            otherWebServer.shutdown()
                          .toCompletableFuture()
                          .join();
        }

    }
}
