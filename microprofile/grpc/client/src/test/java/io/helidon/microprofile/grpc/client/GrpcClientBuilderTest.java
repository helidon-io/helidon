/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import javax.inject.Singleton;

import io.helidon.grpc.client.ClientMethodDescriptor;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.core.JavaMarshaller;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.GrpcMarshaller;
import io.helidon.microprofile.grpc.core.GrpcMethod;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcClientBuilderTest {
    @Test
    public void shouldUseServiceNameFromAnnotation() {
        ServiceOne service = new ServiceOne();
        GrpcClientBuilder builder = GrpcClientBuilder.create(service);
        ClientServiceDescriptor.Builder descriptorBuilder = builder.build();

        assertThat(descriptorBuilder.name(), is("ServiceOne/foo"));
    }

    @Test
    public void shouldUseDefaultServiceName() {
        ServiceTwo service = new ServiceTwo();
        GrpcClientBuilder builder = GrpcClientBuilder.create(service);
        ClientServiceDescriptor.Builder descriptorBuilder = builder.build();

        assertThat(descriptorBuilder.name(), is("ServiceTwo"));
    }

    @Test
    public void shouldCreateServiceFromInstance() {
        ServiceOne service = new ServiceOne();
        assertServiceOne(GrpcClientBuilder.create(service));
    }

    @Test
    public void shouldCreateServiceFromClass() {
        assertServiceOne(GrpcClientBuilder.create(ServiceOne.class));
    }

    public void assertServiceOne(GrpcClientBuilder builder) {
        ClientServiceDescriptor.Builder descriptorBuilder = builder.build();

        ClientServiceDescriptor descriptor = descriptorBuilder.build();
        assertThat(descriptor.name(), is("ServiceOne/foo"));
        assertThat(descriptor.methods().size(), is(4));

        ClientMethodDescriptor ClientMethodDescriptor;
        io.grpc.MethodDescriptor grpcDescriptor;

        ClientMethodDescriptor = descriptor.method("unary");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("unary"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/unary"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("clientStreaming");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("clientStreaming"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/clientStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("serverStreaming");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("serverStreaming"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/serverStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("bidiStreaming");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("bidiStreaming"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/bidiStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    public void shouldCreateServiceWithMethodNamesFromAnnotation() {
        ServiceTwo service = new ServiceTwo();
        GrpcClientBuilder builder = GrpcClientBuilder.create(service);
        ClientServiceDescriptor.Builder descriptorBuilder = builder.build();

        ClientServiceDescriptor descriptor = descriptorBuilder.build();
        assertThat(descriptor.name(), is("ServiceTwo"));
        assertThat(descriptor.methods().size(), is(4));

        ClientMethodDescriptor ClientMethodDescriptor;
        io.grpc.MethodDescriptor grpcDescriptor;

        ClientMethodDescriptor = descriptor.method("One");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("One"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/One"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("Two");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("Two"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Two"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("Three");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("Three"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Three"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        ClientMethodDescriptor = descriptor.method("Four");
        assertThat(ClientMethodDescriptor, is(notNullValue()));
        assertThat(ClientMethodDescriptor.name(), is("Four"));

        grpcDescriptor = ClientMethodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Four"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Grpc(name = "ServiceOne/foo")
    public static class ServiceOne {
        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }

        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
        public StreamObserver<String> clientStreaming(StreamObserver<String> observer) {
            return null;
        }

        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
        public void serverStreaming(String param, StreamObserver<String> observer) {
        }

        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
        public StreamObserver<String> bidiStreaming(StreamObserver<String> observer) {
            return null;
        }
    }

    @Grpc
    public static class ServiceTwo {
        @GrpcMethod(name = "One", type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }

        @GrpcMethod(name = "Two", type = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
        public StreamObserver<String> clientStreaming(StreamObserver<String> observer) {
            return null;
        }

        @GrpcMethod(name = "Three", type = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
        public void serverStreaming(String param, StreamObserver<String> observer) {
        }

        @GrpcMethod(name = "Four", type = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
        public StreamObserver<String> bidiStreaming(StreamObserver<String> observer) {
            return null;
        }
    }

    @Grpc
    @GrpcMarshaller("stub")
    public static class ServiceThree {
        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }
    }

    @Grpc
    @GrpcMarshaller("stub")
    public static class ServiceFour {
        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        @GrpcMarshaller("stub")
        public void unary(String param, StreamObserver<String> observer) {
        }
    }

    @Grpc
    @Singleton
    public static class ServiceFive {
        @GrpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        @GrpcMarshaller("stub")
        public void unary(String param, StreamObserver<ServiceFive> observer) {
            observer.onNext(this);
            observer.onCompleted();
        }
    }
}
