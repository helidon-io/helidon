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

package io.helidon.microprofile.grpc.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Singleton;

import io.helidon.grpc.core.JavaMarshaller;
import io.helidon.grpc.server.MethodDescriptor;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.GrpcMarshaller;
import io.helidon.microprofile.grpc.core.RpcMethod;
import io.helidon.microprofile.grpc.core.RpcService;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class GrpcServiceBuilderTest {

    private BeanManager beanManager;

    @BeforeEach
    public void setup() {
        beanManager = mock(BeanManager.class);
        Instance instance = mock(Instance.class);

        when(beanManager.createInstance()).thenReturn(instance);
    }

    @Test
    public void shouldUseServiceNameFromAnnotation() {
        ServiceOne service = new ServiceOne();
        GrpcServiceBuilder modeller = GrpcServiceBuilder.create(service, beanManager);
        ServiceDescriptor descriptor = modeller.build();

        assertThat(descriptor.name(), is("ServiceOne/foo"));
    }

    @Test
    public void shouldCreateDescriptorFoServiceWithNestedGenericParameters() {
        GrpcServiceBuilder modeller = GrpcServiceBuilder.create(ServiceSix.class, beanManager);
        ServiceDescriptor descriptor = modeller.build();
        assertThat(descriptor.name(), is(ServiceSix.class.getSimpleName()));
    }

    @Test
    public void shouldUseDefaultServiceName() {
        ServiceTwo service = new ServiceTwo();
        GrpcServiceBuilder modeller = GrpcServiceBuilder.create(service, beanManager);
        ServiceDescriptor descriptor = modeller.build();

        assertThat(descriptor.name(), is("ServiceTwo"));
    }

    @Test
    public void shouldCreateServiceFromInstance() {
        ServiceOne service = new ServiceOne();
        assertServiceOne(GrpcServiceBuilder.create(service, beanManager));
    }

    @Test
    public void shouldCreateServiceFromClass() {
        assertServiceOne(GrpcServiceBuilder.create(ServiceOne.class, beanManager));
    }

    @Test
    public void shouldCreateServiceFromClassWithoutBeanManager() {
        assertServiceOne(GrpcServiceBuilder.create(ServiceOne.class, null));
    }

    public void assertServiceOne(GrpcServiceBuilder builder) {
        ServiceDescriptor descriptor = builder.build();
        assertThat(descriptor.name(), is("ServiceOne/foo"));
        assertThat(descriptor.methods().size(), is(4));

        MethodDescriptor methodDescriptor;
        io.grpc.MethodDescriptor grpcDescriptor;

        methodDescriptor = descriptor.method("unary");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("unary"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/unary"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("clientStreaming");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("clientStreaming"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/clientStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("serverStreaming");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("serverStreaming"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/serverStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("bidiStreaming");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("bidiStreaming"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceOne/foo/bidiStreaming"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    public void shouldCreateServiceWithMethodNamesFromAnnotation() {
        ServiceTwo service = new ServiceTwo();
        GrpcServiceBuilder builder = GrpcServiceBuilder.create(service, beanManager);

        ServiceDescriptor descriptor = builder.build();
        assertThat(descriptor.name(), is("ServiceTwo"));
        assertThat(descriptor.methods().size(), is(4));

        MethodDescriptor methodDescriptor;
        io.grpc.MethodDescriptor grpcDescriptor;

        methodDescriptor = descriptor.method("One");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("One"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/One"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("Two");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("Two"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Two"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("Three");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("Three"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Three"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));

        methodDescriptor = descriptor.method("Four");
        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.name(), is("Four"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getFullMethodName(), is("ServiceTwo/Four"));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @SuppressWarnings("unchecked")
    public void assertSingleton(GrpcServiceBuilder builder) {
        ServiceDescriptor descriptor = builder.build();

        MethodDescriptor methodDescriptor = descriptor.method("unary");
        ServerCallHandler handler = methodDescriptor.callHandler();

        ServerCall<String, ServiceFive> callOne = mock(ServerCall.class);
        ServerCall<String, ServiceFive> callTwo = mock(ServerCall.class);
        when(callOne.getMethodDescriptor()).thenReturn(methodDescriptor.descriptor());
        when(callTwo.getMethodDescriptor()).thenReturn(methodDescriptor.descriptor());

        ServerCall.Listener listenerOne = handler.startCall(callOne, new Metadata());
        ServerCall.Listener listenerTwo = handler.startCall(callTwo, new Metadata());

        listenerOne.onMessage("foo");
        listenerOne.onHalfClose();
        listenerTwo.onMessage("foo");
        listenerTwo.onHalfClose();

        ArgumentCaptor<ServiceFive> captorOne = ArgumentCaptor.forClass(ServiceFive.class);
        ArgumentCaptor<ServiceFive> captorTwo = ArgumentCaptor.forClass(ServiceFive.class);

        verify(callOne).sendMessage(captorOne.capture());
        verify(callTwo).sendMessage(captorTwo.capture());

        assertThat(captorOne.getValue(), is(sameInstance(captorTwo.getValue())));
    }

    @RpcService(name = "ServiceOne/foo")
    public static class ServiceOne {
        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }

        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
        public StreamObserver<String> clientStreaming(StreamObserver<String> observer) {
            return null;
        }

        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
        public void serverStreaming(String param, StreamObserver<String> observer) {
        }

        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
        public StreamObserver<String> bidiStreaming(StreamObserver<String> observer) {
            return null;
        }
    }

    @RpcService
    public static class ServiceTwo {
        @RpcMethod(name = "One", type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }

        @RpcMethod(name = "Two", type = io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
        public StreamObserver<String> clientStreaming(StreamObserver<String> observer) {
            return null;
        }

        @RpcMethod(name = "Three", type = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
        public void serverStreaming(String param, StreamObserver<String> observer) {
        }

        @RpcMethod(name = "Four", type = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
        public StreamObserver<String> bidiStreaming(StreamObserver<String> observer) {
            return null;
        }
    }

    @RpcService
    @GrpcMarshaller("stub")
    public static class ServiceThree {
        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public void unary(String param, StreamObserver<String> observer) {
        }
    }

    @RpcService
    @GrpcMarshaller("stub")
    public static class ServiceFour {
        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        @GrpcMarshaller("stub")
        public void unary(String param, StreamObserver<String> observer) {
        }
    }

    @RpcService
    @Singleton
    public static class ServiceFive {
        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        @GrpcMarshaller("stub")
        public void unary(String param, StreamObserver<ServiceFive> observer) {
            observer.onNext(this);
            observer.onCompleted();
        }
    }

    @RpcService
    public static class ServiceSix {
        @RpcMethod(type = io.grpc.MethodDescriptor.MethodType.UNARY)
        public List<Map<Integer, String>> unary(List<Map<Integer, String>> param) {
            return Collections.singletonList(Collections.singletonMap(1, "One"));
        }
    }
}
