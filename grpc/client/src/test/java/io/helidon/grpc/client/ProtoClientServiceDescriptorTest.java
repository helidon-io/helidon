/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor.MethodType;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.StringService;

import static io.helidon.grpc.client.GrpcClientTestUtil._toLower;
import static io.helidon.grpc.client.GrpcClientTestUtil.protoStringSvcDesc;
import static io.helidon.grpc.client.test.Strings.StringMessage;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProtoClientServiceDescriptorTest {

    @BeforeAll
    static void startProtoBasedStringService() {
        GrpcClientTestUtil.initProtoBasedStringServiceDesc();
    }

    static ClientServiceDescriptor.Builder createEmptyStringServiceDescBuilder() {
        return ClientServiceDescriptor.builder("StringService", StringService.class);
    }

    @Test
    public void shouldHaveCorrectNameAndNumberOfMethods() {

        assertThat(protoStringSvcDesc.proto(), notNullValue());
        assertThat(protoStringSvcDesc.serviceName(), equalTo("StringService"));
        assertThat(protoStringSvcDesc.methods().size(), equalTo(5));
        assertThat(protoStringSvcDesc.context().size(), equalTo(0));
        assertThat(protoStringSvcDesc.interceptors().size(), equalTo(0));
        assertThat(protoStringSvcDesc.metricType(), equalTo(MetricType.INVALID));
        assertThat(protoStringSvcDesc.serviceClass(), equalTo(StringService.class));

        checkPreConditions(protoStringSvcDesc, "Lower", MethodType.UNARY);
        checkPreConditions(protoStringSvcDesc, "Upper", MethodType.UNARY);
        checkPreConditions(protoStringSvcDesc, "Split", MethodType.SERVER_STREAMING);
        checkPreConditions(protoStringSvcDesc, "Join", MethodType.CLIENT_STREAMING);
        checkPreConditions(protoStringSvcDesc, "Echo", MethodType.BIDI_STREAMING);

    }

    @Test
    public void shouldHaveCorrectMethodTypesAndMarshallers() {

        assertThat(protoStringSvcDesc.proto(), notNullValue());

        checkPreConditions(protoStringSvcDesc, "Lower", MethodType.UNARY);
        checkPreConditions(protoStringSvcDesc, "Upper", MethodType.UNARY);
        checkPreConditions(protoStringSvcDesc, "Split", MethodType.SERVER_STREAMING);
        checkPreConditions(protoStringSvcDesc, "Join", MethodType.CLIENT_STREAMING);
        checkPreConditions(protoStringSvcDesc, "Echo", MethodType.BIDI_STREAMING);

    }

    // Custom built ClientServiceDescriptor

    @Test
    public void shouldHaveZeroMethodsByDefault() {
        ClientServiceDescriptor svcDesc = createEmptyStringServiceDescBuilder().build();
        assertThat(svcDesc.methods().size(), equalTo(0));
        assertThat(svcDesc.serviceClass(), equalTo(StringService.class));
        assertThat(svcDesc.serviceName(), equalTo("StringService"));
        assertThat(svcDesc.metricType(), equalTo(MetricType.INVALID));
        assertThat(svcDesc.proto(), equalTo(null));
        assertThat(svcDesc.context().size(), equalTo(0));
        assertThat(svcDesc.interceptors().size(), equalTo(0));
    }

    @Test
    public void createUnaryMethodFromExistingMethodDesc() {

        ClientServiceDescriptor.Builder bldr = createEmptyStringServiceDescBuilder();
        ClientMethodDescriptor<StringMessage, StringMessage> cmd =
                ClientMethodDescriptor.builder(_toLower)
                        .requestType(StringMessage.class)
                        .responseType(StringMessage.class)
                        .build();
        bldr.registerMethod(cmd);

        // Build GrpcServiceClient
        Channel ch = ManagedChannelBuilder.forAddress("localhost", GrpcClientTestUtil.grpcPort).usePlaintext().build();
        GrpcServiceClient grpcClient = GrpcServiceClient.builder()
                .channel(ch)
                .callOptions(CallOptions.DEFAULT)
                .clientServiceDescriptor(bldr.build())
                .build();
    }

    private boolean checkPreConditions(ClientServiceDescriptor svcDesc,
                                       String name,
                                       MethodType methodType) {

        ClientMethodDescriptor<StringMessage, StringMessage> cmd = svcDesc.method(name);

        // Check method names & full method names.
        assertThat(cmd.name(), equalTo(name));
        assertThat(cmd.descriptor().getFullMethodName(), equalTo("StringService/" + name));

        // Check types
        assertThat(cmd.descriptor().getType(), equalTo(methodType));
        assertThat(cmd.requestType(), equalTo(StringMessage.class));
        assertThat(cmd.responseType(), equalTo(StringMessage.class));

        // Check Marshallers
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo("io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller"));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo("io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller"));

        return true;
    }

}
