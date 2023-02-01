/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.http2.webserver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

class Http2WebServerStopIdleTest {

    private final Tls insecureTls;

    Http2WebServerStopIdleTest() {;
        this.insecureTls = Tls.builder()
                // insecure setup, as we have self-signed certificate
                .trustAll(true)
                .build();
    }

    @Test
    void stopWhenIdleExpectTimelyStopHttp2() throws IOException, InterruptedException {
        KeyConfig privateKeyConfig = KeyConfig.keystoreBuilder()
                .keystore(Resource.create("certificate.p12"))
                .keystorePassphrase("helidon")
                .build();
        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();
        WebServer webServer = WebServer.builder()
                .socket("https", socketBuilder -> socketBuilder.tls(tls))
                .routing(router -> router.get("ok", (req, res) -> res.send("ok")))
                .build();
        webServer.start();

        int port = webServer.port("https");
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(insecureTls.sslContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(HttpRequest.newBuilder()
                                .timeout(Duration.ofSeconds(5))
                                .uri(URI.create("https://localhost:" + port + "/ok"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("ok"));

        long startMillis = System.currentTimeMillis();
        webServer.stop();
        int stopExecutionTimeInMillis = (int) (System.currentTimeMillis() - startMillis);
        assertThat(stopExecutionTimeInMillis, is(lessThan(500)));
    }
}
