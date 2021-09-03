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

import io.helidon.grpc.client.test.Echo;
import io.helidon.grpc.client.test.EchoServiceGrpc;
import io.helidon.grpc.core.JavaMarshaller;

import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;

public class ClientMethodDescriptorTest {

    private io.grpc.MethodDescriptor.Builder grpcDescriptor;

    @BeforeEach
    public void setup() {
        grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"))
                .toBuilder();
    }

    @Test
    public void shouldCreateMethodDescriptorFromGrpcDescriptor() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.create("FooService",
                                                                          "foo",
                                                                          grpcDescriptor);

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.interceptors(), is(emptyIterable()));

        MethodDescriptor expected = grpcDescriptor.build();
        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("FooService/foo"));
        assertThat(methodDescriptor.getType(), is(expected.getType()));
        assertThat(methodDescriptor.getRequestMarshaller(), is(expected.getRequestMarshaller()));
        assertThat(methodDescriptor.getResponseMarshaller(), is(expected.getResponseMarshaller()));
    }

    @Test
    public void shouldCreateBidirectionalMethod() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.bidirectional("FooService", "foo").build();
        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("FooService/foo"));
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.BIDI_STREAMING));
    }

    @Test
    public void shouldCreateClientStreamingMethod() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.clientStreaming("FooService", "foo").build();
        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("FooService/foo"));
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.CLIENT_STREAMING));
    }

    @Test
    public void shouldCreateServerStreamingMethod() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.serverStreaming("FooService", "foo").build();
        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("FooService/foo"));
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.SERVER_STREAMING));
    }

    @Test
    public void shouldCreateUnaryMethod() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.unary("FooService", "foo").build();
        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("FooService/foo"));
        assertThat(methodDescriptor.getType(), is(MethodDescriptor.MethodType.UNARY));
    }

    @Test
    public void shouldSetName() {
        ClientMethodDescriptor.Builder builder = ClientMethodDescriptor
                .unary("FooService", "foo");

        builder.fullName("Foo/Bar");

        ClientMethodDescriptor descriptor = builder.build();

        assertThat(descriptor.name(), is("Bar"));
        assertThat(descriptor.descriptor().getFullMethodName(), is("Foo/Bar"));
    }

    @Test
    public void testMarshallerTypesForNonProtoBuilder() {
        ClientMethodDescriptor cmd = ClientMethodDescriptor.unary("foo", "bar").build();

        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));

    }

    @Test
    public void testMarshallerTypesForProtoBuilder() {
        ClientMethodDescriptor descriptor = ClientMethodDescriptor
                .unary("EchoService", "Echo")
                .requestType(Echo.EchoRequest.class)
                .responseType(Echo.EchoResponse.class)
                .build();

        MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getRequestMarshaller(), instanceOf(MethodDescriptor.PrototypeMarshaller.class));
        assertThat(methodDescriptor.getResponseMarshaller(), instanceOf(MethodDescriptor.PrototypeMarshaller.class));
    }
}
