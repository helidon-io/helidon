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

import java.util.Locale;

import com.google.protobuf.StringValue;
import io.helidon.examples.grpc.strings.Strings;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import org.junit.jupiter.api.Test;

@ServerTest
class GrpcTest {

    private final GrpcClient grpcClient;

    private GrpcTest(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder builder) {
        builder.addRouting(GrpcRouting.builder()
                        .unary(Strings.getDescriptor(),
                                "StringService",
                                "Upper",
                                GrpcTest::blockingGrpcUpper));
    }

    private static Strings.StringMessage blockingGrpcUpper(Strings.StringMessage reqT) {
        return Strings.StringMessage.newBuilder()
                .setText(reqT.getText().toUpperCase(Locale.ROOT))
                .build();
    }

    @Test
    void testSimpleCall() {
        GrpcServiceDescriptor serviceDescriptor =
                GrpcServiceDescriptor.builder()
                        .serviceName("StringService")
                        .putMethod("Upper",
                                GrpcClientMethodDescriptor.unary("StringService", "Upper")
                                        .requestType(StringValue.class)
                                        .responseType(StringValue.class)
                                        .build())
                        .build();

        String r = grpcClient.serviceClient(serviceDescriptor)
                .unary("Upper", StringValue.of("hello"));
    }
}
