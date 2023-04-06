/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.protocols;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.examples.nima.grpc.strings.Strings;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.grpc.webserver.GrpcRouting;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http1.Http1Route;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import io.helidon.nima.websocket.webserver.WsRouting;

import io.grpc.stub.StreamObserver;

import static io.helidon.common.http.Http.Method.GET;
import static io.helidon.nima.grpc.webserver.ResponseHelper.complete;

/**
 * Example showing supported protocols.
 */
public class ProtocolsMain {
    private static final byte[] RESPONSE = "Hello from Níma!".getBytes(StandardCharsets.UTF_8);

    private ProtocolsMain() {
    }

    /**
     * Main method.
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
                .defaultSocket(builder -> builder.port(8080)
                        .host("127.0.0.1"))
                .socket("https",
                        builder -> builder.port(8081)
                                .host("127.0.0.1")
                                .tls(tls)
                                .receiveBufferSize(4096)
                                .backlog(8192)
                )
                .routing(router -> router
                        .get("/", (req, res) -> res.send(RESPONSE))
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/2 route"))))
                .addRouting(GrpcRouting.builder()
                                    .unary(Strings.getDescriptor(),
                                           "StringService",
                                           "Upper",
                                           ProtocolsMain::grpcUpper))
                .addRouting(WsRouting.builder()
                                    .endpoint("/tyrus/echo", ProtocolsMain::wsEcho))
                .start();
    }

    private static void grpcUpper(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String requestText = request.getText();
        System.out.println("grpc request: " + requestText);
        complete(observer, Strings.StringMessage.newBuilder()
                .setText(requestText.toUpperCase(Locale.ROOT))
                .build());
    }

    private static WsListener wsEcho() {
        return new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                session.send(text, last);
                System.out.println("websocket request " + text);
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                System.out.println("websocket closed (" + status + " " + reason + ")");
            }
        };
    }

    private static KeyConfig privateKey() {
        String password = "helidon";

        return KeyConfig.keystoreBuilder()
                .keystore(Resource.create("certificate.p12"))
                .keystorePassphrase(password)
                .build();
    }
}
