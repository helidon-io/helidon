/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.benchmark.h2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.pki.KeyConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * HTTP/2 benchmark main.
 * Opens server on localhost:8080 and exposes {@code /plaintext} and {@code /json} endpoints.
 */
public class Main {
    private Main() {
    }

    /**
     * Start the server.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        KeyConfig privateKeyConfig = privateKey();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        WebServer.builder()
                .defaultSocket(socket -> socket.connectionOptions(builder -> builder
                                .readTimeout(Duration.ZERO)
                                .connectTimeout(Duration.ZERO)
                                .socketSendBufferSize(64000)
                                .socketReceiveBufferSize(64000))
                        .writeQueueLength(4000)
                        .port(8080)
                        .host("127.0.0.1")
                        .backlog(8192)
                )
                .routing(router -> router
                        .get("/plaintext", new PlaintextHandler())
                        .get("/", new PlaintextHandler())
                )
                .socket("https",
                        builder -> builder.port(8081)
                                .host("127.0.0.1")
                                .tls(tls)
                                .backlog(8192)
                                .writeQueueLength(4000)
                )
                .build()
                .start();
    }

    private static KeyConfig privateKey() {
        String password = "helidon";

        return KeyConfig.keystoreBuilder()
                .keystore(Resource.create("certificate.p12"))
                .keystorePassphrase(password)
                .build();
    }

    private static class PlaintextHandler implements Handler {
        private static final HeaderValue CONTENT_TYPE = HeaderValue.createCached(Header.CONTENT_TYPE,
                                                                                 "text/plain; charset=UTF-8");
        private static final HeaderValue CONTENT_LENGTH = HeaderValue.createCached(Header.CONTENT_LENGTH, "13");
        private static final HeaderValue SERVER = HeaderValue.createCached(Header.SERVER, "NIMA");

        private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header(CONTENT_LENGTH);
            res.header(CONTENT_TYPE);
            res.header(SERVER);
            res.send(RESPONSE_BYTES);
        }
    }
}
