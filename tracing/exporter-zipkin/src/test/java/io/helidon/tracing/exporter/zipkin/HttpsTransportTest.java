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

package io.helidon.tracing.exporter.zipkin;

import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("removal")
class HttpsTransportTest {
    @Test
    void exportsOverHttpsUsingSystemTrustStore() throws Exception {
        char[] password = "password".toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = getClass().getResourceAsStream("/server.p12")) {
            store.load(input, password);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        AtomicInteger calls = new AtomicInteger();
        WebServer server = WebServer.builder().port(0).host("localhost")
                .tls(tls -> tls.sslContext(context))
                .routing(routes -> routes.post("/api/v2/spans", (request, response) -> {
                    request.content().as(byte[].class);
                    calls.incrementAndGet();
                    response.status(202).send();
                })).build().start();
        try {
            try (var exporter = ZipkinSpanExporter.builder()
                    .endpoint(URI.create("https://localhost:" + server.port() + "/api/v2/spans")).build()) {
                assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
                assertThat(calls.get(), is(1));
            }
        } finally {
            server.stop();
        }
    }

}
