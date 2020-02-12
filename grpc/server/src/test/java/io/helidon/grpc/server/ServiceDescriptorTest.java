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

package io.helidon.grpc.server;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.grpc.core.JavaMarshaller;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.PriorityBag;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ServiceDescriptor} unit tests.
 */
public class ServiceDescriptorTest {

    @Test
    public void shouldHaveCorrectName() {
        GrpcService service = createMockService();
        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .build();

        assertThat(descriptor.name(), is(service.name()));
    }

    @Test
    public void shouldHaveZeroContextValuesByDefault() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .build();

        Map<Context.Key<?>, Object> map = descriptor.context();
        assertThat(map, is(notNullValue()));
        assertThat(map.isEmpty(), is(true));
    }

    @Test
    public void shouldAddContextValue() {
        Context.Key<String> key = Context.key("test");
        String value = "test-value";

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .addContextValue(key, value)
                .build();

        Map<Context.Key<?>, Object> map = descriptor.context();
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map, hasEntry(key, value));
    }

    @Test
    public void shouldAddMultipleContextValues() {
        Context.Key<String> key1 = Context.key("test-1");
        String value1 = "test-value-1";
        Context.Key<String> key2 = Context.key("test-2");
        String value2 = "test-value-2";

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .addContextValue(key1, value1)
                .addContextValue(key2, value2)
                .build();

        Map<Context.Key<?>, Object> map = descriptor.context();
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(2));
        assertThat(map, hasEntry(key1, value1));
        assertThat(map, hasEntry(key2, value2));
    }

    @Test
    public void shouldHaveDefaultHealthCheck() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .build();

        assertThat(descriptor.healthCheck(), is(notNullValue()));

        HealthCheckResponse response = descriptor.healthCheck().call();

        assertThat(response.getName(), is(notNullValue()));
        assertThat(response.getState(), is(HealthCheckResponse.State.UP));
    }

    @Test
    public void shouldHaveSpecifiedHealthCheck() {
        HealthCheck healthCheck = mock(HealthCheck.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .healthCheck(healthCheck)
                .build();

        assertThat(descriptor.healthCheck(), is(sameInstance(healthCheck)));
    }

    @Test
    public void shouldAddZeroInterceptors() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .intercept()
                .build();

        assertThat(descriptor.interceptors(), is(emptyIterable()));
    }

    @Test
    public void shouldAddOneInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .intercept(interceptor)
                .build();

        assertThat(descriptor.interceptors(), contains(interceptor));
    }

    @Test
    public void shouldAddMultipleInterceptors() {
        ServerInterceptor interceptor1 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor2 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor3 = mock(ServerInterceptor.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .intercept(interceptor1, interceptor2)
                .intercept(interceptor3)
                .build();

        assertThat(descriptor.interceptors(), contains(interceptor1, interceptor2, interceptor3));
    }

    @Test
    public void shouldHaveZeroMethods() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService()).build();

        assertThat(descriptor.methods(), is(emptyIterable()));
    }

    @Test
    public void shouldAddBidirectionalMethod() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .bidirectional("methodOne", this::dummyBiDi)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddBidirectionalMethodWithConfigurer() {
        MethodDescriptor.Configurer configurer = mock(MethodDescriptor.Configurer.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .bidirectional("methodOne", this::dummyBiDi, configurer)
                .build();

        verify(configurer).configure(notNull());

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    public void shouldAddClientStreaminglMethod() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .clientStreaming("methodOne", this::dummyClientStreaming)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddClientStreamingMethodWithConfigurer() {
        MethodDescriptor.Configurer configurer = mock(MethodDescriptor.Configurer.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .clientStreaming("methodOne", this::dummyClientStreaming, configurer)
                .build();

        verify(configurer).configure(notNull());

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    public void shouldAddServerStreaminglMethod() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .serverStreaming("methodOne", this::dummyServerStreaming)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddServerStreamingMethodWithConfigurer() {
        MethodDescriptor.Configurer configurer = mock(MethodDescriptor.Configurer.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .serverStreaming("methodOne", this::dummyServerStreaming, configurer)
                .build();

        verify(configurer).configure(notNull());

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    public void shouldAddUnaryMethod() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming)
                .build();

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddUnaryMethodWithConfigurer() {
        MethodDescriptor.Configurer configurer = mock(MethodDescriptor.Configurer.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming, configurer)
                .build();

        verify(configurer).configure(notNull());

        MethodDescriptor methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(descriptor.methods().size(), is(1));
        assertThat(descriptor.methods(), contains(methodDescriptor));

        assertThat(methodDescriptor.name(), is("methodOne"));
        assertThat(methodDescriptor.callHandler(), is(notNullValue()));

        io.grpc.MethodDescriptor grpcDescriptor = methodDescriptor.descriptor();
        assertThat(grpcDescriptor, is(notNullValue()));
        assertThat(grpcDescriptor.getType(), is(io.grpc.MethodDescriptor.MethodType.UNARY));
        assertThat(grpcDescriptor.getFullMethodName(), is("foo/methodOne"));
        assertThat(grpcDescriptor.getRequestMarshaller(), is(instanceOf(JavaMarshaller.class)));
        assertThat(grpcDescriptor.getResponseMarshaller(), is(instanceOf(JavaMarshaller.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldAddZeroMethodLevelInterceptors() {
        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming)
                .intercept("methodOne")
                .build();

        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.interceptors(), is(emptyIterable()));
    }

    @Test
    public void shouldAddOneMethodLevelInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming)
                .intercept("methodOne", interceptor)
                .build();

        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.interceptors(), contains(interceptor));
    }

    @Test
    public void shouldAddMultipleMethodLevelInterceptors() {
        ServerInterceptor interceptor1 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor2 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor3 = mock(ServerInterceptor.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming)
                .intercept("methodOne", interceptor1, interceptor2)
                .intercept("methodOne", interceptor3)
                .build();

        MethodDescriptor<?, ?> methodDescriptor = descriptor.method("methodOne");

        assertThat(methodDescriptor, is(notNullValue()));
        assertThat(methodDescriptor.interceptors(), contains(interceptor1, interceptor2, interceptor3));
    }

    @Test
    public void shouldAddMethodLevelInterceptorsToDifferentMethods() {
        ServerInterceptor interceptor1 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor2 = mock(ServerInterceptor.class);
        ServerInterceptor interceptor3 = mock(ServerInterceptor.class);

        ServiceDescriptor descriptor = ServiceDescriptor.builder(createMockService())
                .unary("methodOne", this::dummyServerStreaming)
                .intercept("methodOne", interceptor1, interceptor2)
                .unary("methodTwo", this::dummyServerStreaming)
                .intercept("methodTwo", interceptor3)
                .build();

        MethodDescriptor<?, ?> methodDescriptor1 = descriptor.method("methodOne");
        MethodDescriptor<?, ?> methodDescriptor2 = descriptor.method("methodTwo");

        assertThat(methodDescriptor1, is(notNullValue()));
        assertThat(methodDescriptor1.interceptors(), contains(interceptor1, interceptor2));
        assertThat(methodDescriptor2, is(notNullValue()));
        assertThat(methodDescriptor2.interceptors(), contains(interceptor3));
    }

    @Test
    public void shouldSetMarshaller() {
        io.grpc.MethodDescriptor.Marshaller marshaller = mock(io.grpc.MethodDescriptor.Marshaller.class);
        MarshallerSupplier supplier = new MarshallerSupplier() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> io.grpc.MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
                return marshaller;
            }
        };

        ServiceDescriptor descriptor = ServiceDescriptor
                .builder(createMockService())
                .marshallerSupplier(supplier)
                .unary("bar", this::dummyUnary)
                .build();

        io.grpc.MethodDescriptor<?, ?> methodDescriptor = descriptor.bindableService(PriorityBag.create())
                .bindService()
                .getMethod("foo/bar")
                .getMethodDescriptor();

        assertThat(methodDescriptor.getResponseMarshaller(), is(sameInstance(marshaller)));
        assertThat(methodDescriptor.getRequestMarshaller(), is(sameInstance(marshaller)));
    }

    @Test
    public void shouldBuildFromBindableService() {
        BindableService service = new EchoStub();
        ServerServiceDefinition definition = service.bindService();
        io.grpc.ServiceDescriptor grpcDescriptor = definition.getServiceDescriptor();

        ServiceDescriptor descriptor = ServiceDescriptor.builder(service).build();

        assertThat(descriptor.name(), is(grpcDescriptor.getName()));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor actualDescriptor = ssd.getServiceDescriptor();
        assertThat(actualDescriptor, is(notNullValue()));
        assertThat(actualDescriptor.getName(), is(grpcDescriptor.getName()));

        Map<String, io.grpc.MethodDescriptor<?, ?>> methods = grpcDescriptor.getMethods()
                .stream()
                .collect(Collectors.toMap(io.grpc.MethodDescriptor::getFullMethodName, m -> m));

        Collection<io.grpc.MethodDescriptor<?, ?>> methodsActual = actualDescriptor.getMethods();

        for (io.grpc.MethodDescriptor<?, ?> method : methodsActual) {
            assertThat(method.toString(), is(methods.get(method.getFullMethodName()).toString()));
        }
    }

    @Test
    public void shouldOverrideServiceName() {
        BindableService service = new EchoStub();

        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .name("Foo")
                .build();

        assertThat(descriptor.name(), is("Foo"));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor actualDescriptor = ssd.getServiceDescriptor();
        assertThat(actualDescriptor, is(notNullValue()));
        assertThat(actualDescriptor.getName(), is("Foo"));

        Collection<io.grpc.MethodDescriptor<?, ?>> methods = actualDescriptor.getMethods();

        for (io.grpc.MethodDescriptor<?, ?> method : methods) {
            assertThat(method.getFullMethodName().startsWith("Foo/"), is(true));
        }
    }

    @Test
    public void shouldBuildFromProtoFile() {
        GrpcService service = mock(GrpcService.class);

        when(service.name()).thenReturn("EchoService");

        Descriptors.FileDescriptor protoDescriptor = Echo.getDescriptor();

        ServiceDescriptor descriptor = ServiceDescriptor.builder(service)
                .proto(protoDescriptor)
                .unary("Echo", this::dummyUnary)
                .build();

        assertThat(descriptor.name(), is("EchoService"));

        BindableService bindableService = descriptor.bindableService(PriorityBag.create());
        assertThat(bindableService, is(notNullValue()));

        ServerServiceDefinition ssd = bindableService.bindService();
        assertThat(ssd, is(notNullValue()));

        io.grpc.ServiceDescriptor actualDescriptor = ssd.getServiceDescriptor();
        assertThat(actualDescriptor, is(notNullValue()));
        assertThat(actualDescriptor.getName(), is("EchoService"));
    }


    private StreamObserver<String> dummyBiDi(StreamObserver<String> observer) {
        return null;
    }

    private StreamObserver<String> dummyClientStreaming(StreamObserver<String> observer) {
        return null;
    }

    private void dummyServerStreaming(String request, StreamObserver<String> observer) {
    }

    private void dummyUnary(String request, StreamObserver<String> observer) {
    }

    private GrpcService createMockService() {
        GrpcService service = mock(GrpcService.class);

        when(service.name()).thenReturn("foo");

        return service;
    }

    private class EchoStub
            extends EchoServiceGrpc.EchoServiceImplBase {

    }
}
