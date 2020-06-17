/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.webclient.WebClientTls;
import io.helidon.webclient.WebClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The Pkcs12StoreSslTest.
 */
public class Pkcs12StoreSslTest {

    private static WebClient client;

    @BeforeAll
    public static void createClientAcceptingAllCertificates() {
        client = WebClient.builder()
                .tls(WebClientTls.builder()
                             .trustAll(true)
                             .build())
                .build();
    }

    @Test
    public void testPkcs12() throws Exception {
        WebServer otherWebServer =
                WebServer.builder(Routing.builder()
                                          .any((req, res) -> res.send("It works!"))
                                          .build())
                        .tls(WebServerTls.builder()
                                     .privateKey(KeyConfig.keystoreBuilder()
                                                         .keystore(Resource.create("ssl/certificate.p12"))
                                                         .keystorePassphrase(new char[] {'h', 'e', 'l', 'i', 'd', 'o', 'n'})
                                                         .build()))
                        .build()
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        otherWebServer.start()
                .toCompletableFuture()
                .join();
        try {
            client.get()
                    .uri("https://localhost:" + otherWebServer.port())
                    .request(String.class)
                    .thenAccept(it -> assertThat(it, is("It works!")))
                    .toCompletableFuture()
                    .get();
        } finally {
            otherWebServer.shutdown()
                    .toCompletableFuture()
                    .join();
        }

    }
}
