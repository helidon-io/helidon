/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.Collection;

import io.helidon.grpc.client.test.StringServiceGrpc;

import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServiceDescriptor;
import org.junit.jupiter.api.Test;
import services.TreeMapService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;


public class ClientServiceDescriptorTest {

    @Test
    public void shouldCreateDescriptorFromGrpcServiceDescriptor() {
        ServiceDescriptor grpcDescriptor = StringServiceGrpc.getServiceDescriptor();
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.create(grpcDescriptor);
        String serviceName = "StringService";
        assertThat(descriptor.name(), is(serviceName));
        assertThat(descriptor.interceptors(), is(emptyIterable()));

        Collection<MethodDescriptor<?, ?>> expectedMethods = grpcDescriptor.getMethods();
        Collection<ClientMethodDescriptor> actualMethods = descriptor.methods();
        assertThat(actualMethods.size(), is(expectedMethods.size()));

        for (MethodDescriptor<?, ?> methodDescriptor : expectedMethods) {
            String name = methodDescriptor.getFullMethodName().substring(serviceName.length() + 1);
            ClientMethodDescriptor method = descriptor.method(name);
            assertThat(method.name(), is(name));
            assertThat(method.interceptors(), is(emptyIterable()));
            MethodDescriptor<Object, Object> actualDescriptor = method.descriptor();
            assertThat(actualDescriptor.getType(), is(methodDescriptor.getType()));
        }
    }

    @Test
    public void shouldCreateDescriptorFromBindableService() {
        StringServiceBindableService bindableService = new StringServiceBindableService();
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.create(bindableService);
        String serviceName = "StringService";
        assertThat(descriptor.name(), is(serviceName));
        assertThat(descriptor.interceptors(), is(emptyIterable()));

        Collection<ServerMethodDefinition<?, ?>> expectedMethods = bindableService.bindService().getMethods();
        Collection<ClientMethodDescriptor> actualMethods = descriptor.methods();
        assertThat(actualMethods.size(), is(expectedMethods.size()));

        for (ServerMethodDefinition<?, ?> expectedMethod : expectedMethods) {
            MethodDescriptor<?, ?> methodDescriptor = expectedMethod.getMethodDescriptor();
            String name = methodDescriptor.getFullMethodName().substring(serviceName.length() + 1);
            ClientMethodDescriptor method = descriptor.method(name);
            assertThat(method.name(), is(name));
            assertThat(method.interceptors(), is(emptyIterable()));
            MethodDescriptor<Object, Object> actualDescriptor = method.descriptor();
            assertThat(actualDescriptor.getType(), is(methodDescriptor.getType()));
        }
    }

    @Test
    public void testServiceName() {
        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder("TreeMapService",
                                                                                  TreeMapService.class);
        assertThat(builder.name(), is("TreeMapService"));

        ClientServiceDescriptor descriptor = builder.build();
        assertThat(descriptor.name(), is("TreeMapService"));
    }

    @Test
    public void testDefaultMethodCount() {
        ClientServiceDescriptor svcDesc = ClientServiceDescriptor.builder(TreeMapService.class).build();
        assertThat(svcDesc.methods().size(), equalTo(0));
    }

    @Test
    public void shouldNotAllowNullName() {
        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder(TreeMapService.class);

        assertThrows(NullPointerException.class, () -> builder.name(null));
    }

    @Test
    public void shouldNotAllowEmptyStringName() {
        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder(TreeMapService.class);

        assertThrows(IllegalArgumentException.class, () -> builder.name(""));
    }

    @Test
    public void shouldNotAllowBlankName() {
        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder(TreeMapService.class);

        assertThrows(IllegalArgumentException.class, () -> builder.name("  \t  "));
    }

    @Test
    public void shouldAddBidirectionalMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .bidirectional("foo")
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddBidirectionalMethodWithConfigurer() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .bidirectional("foo", cfg -> cfg.intercept(interceptor))
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        assertThat(method.interceptors(), contains(interceptor));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.BIDI_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddClientStreamingMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .clientStreaming("foo")
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddClientStreamingMethodWithConfigurer() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .clientStreaming("foo", cfg -> cfg.intercept(interceptor))
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        assertThat(method.interceptors(), contains(interceptor));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.CLIENT_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddServerStreamingMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .serverStreaming("foo")
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddServerStreamingMethodWithConfigurer() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .serverStreaming("foo", cfg -> cfg.intercept(interceptor))
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        assertThat(method.interceptors(), contains(interceptor));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.SERVER_STREAMING));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddUnaryMethod() {
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .unary("foo")
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.UNARY));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddUnaryMethodWithConfigurer() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .unary("foo", cfg -> cfg.intercept(interceptor))
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        assertThat(method.interceptors(), contains(interceptor));
        MethodDescriptor<Object, Object> methodDescriptor = method.descriptor();
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.UNARY));
        assertThat(methodDescriptor.getFullMethodName(), is("TreeMapService/foo"));
    }

    @Test
    public void shouldAddInterceptor() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .intercept(interceptor)
                .build();

        assertThat(descriptor.interceptors(), contains(interceptor));
    }

    @Test
    public void shouldAddInterceptors() {
        ClientInterceptor interceptorOne = mock(ClientInterceptor.class);
        ClientInterceptor interceptorTwo = mock(ClientInterceptor.class);
        ClientInterceptor interceptorThree = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .intercept(interceptorOne)
                .intercept(interceptorTwo, interceptorThree)
                .build();

        assertThat(descriptor.interceptors(), containsInAnyOrder(interceptorOne, interceptorTwo, interceptorThree));
    }

    @Test
    public void shouldAddInterceptorToMethod() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        ClientServiceDescriptor descriptor = ClientServiceDescriptor.builder(TreeMapService.class)
                .unary("foo")
                .intercept("foo", interceptor)
                .build();

        ClientMethodDescriptor method = descriptor.method("foo");
        assertThat(method, is(notNullValue()));
        assertThat(method.interceptors(), contains(interceptor));
    }

    @Test
    public void shouldSetNameOnMethods() {
        ClientServiceDescriptor.Builder builder = ClientServiceDescriptor.builder(TreeMapService.class);

        ClientServiceDescriptor descriptor = builder.unary("bar")
                .name("Foo")
                .build();

        ClientMethodDescriptor method = descriptor.method("bar");
        assertThat(method.descriptor().getFullMethodName(), is("Foo/bar"));
    }

    public static class StringServiceBindableService
               extends StringServiceGrpc.StringServiceImplBase {
       }
}
