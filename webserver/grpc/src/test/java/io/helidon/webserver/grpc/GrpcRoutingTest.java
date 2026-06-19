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

package io.helidon.webserver.grpc;

import java.util.List;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcRoutingTest {
    @Test
    void acceptsSameSimpleServiceNameInDifferentProtoPackages() throws Descriptors.DescriptorValidationException {
        GrpcServiceDescriptor alpha = descriptor("alpha");
        GrpcServiceDescriptor beta = descriptor("beta");

        GrpcRouting routing = GrpcRouting.builder()
                .service(alpha)
                .service(beta)
                .build();

        List<String> serviceNames = routing.services()
                .stream()
                .map(GrpcServiceDescriptor::fullName)
                .toList();
        assertThat(serviceNames, is(List.of("alpha.Greeter", "beta.Greeter")));
    }

    @Test
    void rejectsDuplicateFullServiceName() throws Descriptors.DescriptorValidationException {
        GrpcServiceDescriptor alpha = descriptor("alpha");
        GrpcServiceDescriptor duplicate = descriptor("alpha");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> GrpcRouting.builder()
                                                                  .service(alpha)
                                                                  .service(duplicate));

        assertThat(exception.getMessage(), containsString("alpha.Greeter"));
    }

    private static GrpcServiceDescriptor descriptor(String protoPackage)
            throws Descriptors.DescriptorValidationException {
        return GrpcServiceDescriptor.builder(GrpcRoutingTest.class, protoPackage + ".Greeter")
                .proto(proto(protoPackage))
                .build();
    }

    private static Descriptors.FileDescriptor proto(String protoPackage)
            throws Descriptors.DescriptorValidationException {
        DescriptorProtos.DescriptorProto request = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GreetingRequest")
                .build();
        DescriptorProtos.DescriptorProto reply = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GreetingReply")
                .build();
        DescriptorProtos.MethodDescriptorProto method = DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName("SayHello")
                .setInputType("." + protoPackage + ".GreetingRequest")
                .setOutputType("." + protoPackage + ".GreetingReply")
                .build();
        DescriptorProtos.ServiceDescriptorProto service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                .setName("Greeter")
                .addMethod(method)
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(protoPackage + "_greeter.proto")
                .setPackage(protoPackage)
                .addMessageType(request)
                .addMessageType(reply)
                .addService(service)
                .build();

        return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
    }
}
