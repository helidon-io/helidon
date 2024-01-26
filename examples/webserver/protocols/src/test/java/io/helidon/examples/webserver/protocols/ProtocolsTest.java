/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.protocols;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.Test;

@ServerTest
class ProtocolsTest {

    private final WebClient webClient;
    private final WsClient wsClient;
    private final GrpcClient grpcClient;

    private ProtocolsTest(WebClient webClient, WsClient wsClient, GrpcClient grpcClient) {
        this.webClient = webClient;
        this.wsClient = wsClient;
        this.grpcClient = grpcClient;
    }

    @Test
    void test() {
        grpcClient.serviceClient(
                GrpcServiceDescriptor
        )
    }
}
