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

import java.util.Date;

import io.helidon.grpc.client.test.EchoServiceGrpc;
import io.helidon.grpc.client.test.StringServiceGrpc;
import io.helidon.grpc.client.test.Strings.StringMessage;
import io.helidon.grpc.core.JavaMarshaller;
import io.helidon.grpc.core.MarshallerSupplier;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Test;
import services.StringService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;

/**
 * @author Mahesh Kannan
 */
@SuppressWarnings("unchecked")
public class ClientMethodDescriptorTest {

    @Test
    public void shouldCreateMethodDescriptor() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.create("foo", grpcDescriptor);

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), nullValue());
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));

        io.grpc.MethodDescriptor methodDescriptor = descriptor.descriptor();
        assertThat(methodDescriptor.getFullMethodName(), is("EchoService/foo"));

        ClientMethodDescriptor descriptor2 = ClientMethodDescriptor.create("bar", grpcDescriptor);

        assertThat(descriptor2, is(notNullValue()));
        assertThat(descriptor2.name(), is("bar"));
        assertThat(descriptor2.metricType(), nullValue());
        assertThat(descriptor2.context(), is(notNullValue()));
        assertThat(descriptor2.context().size(), is(0));

        io.grpc.MethodDescriptor methodDescriptor2 = descriptor2.descriptor();
        assertThat(methodDescriptor2.getFullMethodName(), is("EchoService/bar"));

        // Also test that the foo descriptor remains unchanged.
        assertThat(methodDescriptor.getFullMethodName(), is("EchoService/foo"));
    }

    @Test
    public void shouldBuildMethodDescriptorWithCounterMetric() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .counted()
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), is(MetricType.COUNTER));
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));
    }

    @Test
    public void shouldBuildMethodDescriptorWithHistogramMetric() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .histogram()
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), is(MetricType.HISTOGRAM));

        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));
    }

    @Test
    public void shouldBuildMethodDescriptorWithMeterMetric() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .metered()
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), is(MetricType.METERED));
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));
    }

    @Test
    public void shouldBuildMethodDescriptorWithTimerMetric() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .timed()
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), is(MetricType.TIMER));
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));
    }

    @Test
    public void shouldBuildMethodDescriptorWithDisabledMetric() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .disableMetrics()
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), is(MetricType.INVALID));
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(0));
    }

    @Test
    public void shouldBuildMethodDescriptorWithContextValue() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        Context.Key<String> key = Context.key("test");
        ClientMethodDescriptor descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .addContextKey(key, "test-value")
                .build();

        assertThat(descriptor, is(notNullValue()));
        assertThat(descriptor.name(), is("foo"));
        assertThat(descriptor.metricType(), nullValue());
        assertThat(descriptor.context(), is(notNullValue()));
        assertThat(descriptor.context().size(), is(1));
        assertThat(descriptor.context().get(key), is("test-value"));
    }

    @Test
    public void shouldAddZeroInterceptors() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor<?, ?> descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .intercept()
                .build();

        assertThat(descriptor.interceptors(), is(emptyIterable()));
    }

    @Test
    public void shouldAddOneInterceptor() {
        ClientInterceptor interceptor = mock(ClientInterceptor.class);
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor<?, ?> descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .intercept(interceptor)
                .build();

        assertThat(descriptor.interceptors(), contains(interceptor));
    }

    @Test
    public void shouldAddMultipleInterceptors() {
        ClientInterceptor interceptor1 = mock(ClientInterceptor.class);
        ClientInterceptor interceptor2 = mock(ClientInterceptor.class);
        ClientInterceptor interceptor3 = mock(ClientInterceptor.class);
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        ClientMethodDescriptor<?, ?> descriptor = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .intercept(interceptor1, interceptor2)
                .intercept(interceptor3)
                .build();

        assertThat(descriptor.interceptors(), contains(interceptor1, interceptor2, interceptor3));
    }

    @Test
    public void shouldSetName() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        Context.Key<String> key = Context.key("test");
        ClientMethodDescriptor cmd = ClientMethodDescriptor.builder("foo", grpcDescriptor)
                .build();
        ClientMethodDescriptor cmd2 = cmd.toBuilder().name("bar")
                .build();

        assertThat(cmd, is(notNullValue()));
        assertThat(cmd.name(), is("foo"));
        assertThat(cmd.descriptor().getFullMethodName(), is("EchoService/foo"));

        assertThat(cmd2, is(notNullValue()));
        assertThat(cmd2.name(), is("bar"));
        assertThat(cmd2.descriptor().getFullMethodName(), is("EchoService/bar"));
    }

    @Test
    public void testIsIdentical() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        Context.Key<String> key = Context.key("test");
        ClientMethodDescriptor cmd = ClientMethodDescriptor.builder("foo", grpcDescriptor).build();
        ClientMethodDescriptor cmd2 = cmd.toBuilder().build();

        assertThat(cmd2, is(notNullValue()));

        assertThat(cmd.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(cmd), is(true));
        assertThat(cmd2.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(new Date()), is(false));
        assertThat(cmd2.isIdentical(new Date()), is(false));
        assertThat(cmd != cmd2, is(true));
    }

    @Test
    public void testToBuilderMethod() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        Context.Key<String> key = Context.key("test");
        ClientMethodDescriptor cmd = ClientMethodDescriptor.builder("Echo", grpcDescriptor).build();
        ClientMethodDescriptor cmd2 = cmd.toBuilder().build();

        assertThat(cmd2, is(notNullValue()));
        assertThat(cmd.name(), equalTo(cmd2.name()));
        assertThat(cmd.name(), equalTo(cmd2.name()));
        assertThat(cmd.metricType(), equalTo(cmd2.metricType()));
        assertThat(cmd.requestType(), equalTo(cmd2.requestType()));
        assertThat(cmd.responseType(), equalTo(cmd2.responseType()));

        assertThat(cmd.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(cmd), is(true));
        assertThat(cmd2.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(new Date()), is(false));
        assertThat(cmd2.isIdentical(new Date()), is(false));
        assertThat(cmd != cmd2, is(true));
    }

    // ------------------ Tests that build the ClientMethodDescriptors from scratch ---------------

    @Test
    public void testDefaultNonProtoBuilder() {
        MethodDescriptor<String, String> md = MethodDescriptor.<String, String>newBuilder()
                .setFullMethodName("EchoService/Echo")
                .setType(MethodDescriptor.MethodType.UNARY)
                .setRequestMarshaller(MarshallerSupplier.defaultInstance().get(String.class))
                .setResponseMarshaller(MarshallerSupplier.defaultInstance().get(String.class))
                .build();
        ClientMethodDescriptor<String, String> cmd = ClientMethodDescriptor.builder("foo", md).build();
        assertThat(cmd.name(), equalTo("foo"));
        assertThat(cmd.descriptor().getFullMethodName(), equalTo("EchoService/foo"));
    }

    @Test
    public void testMarshallerTypesForNonProtoBuilder() {
        MethodDescriptor<String, String> md = MethodDescriptor.<String, String>newBuilder()
                .setFullMethodName("EchoService/Echo")
                .setType(MethodDescriptor.MethodType.UNARY)
                .setRequestMarshaller(MarshallerSupplier.defaultInstance().get(String.class))
                .setResponseMarshaller(MarshallerSupplier.defaultInstance().get(String.class))
                .build();
        ClientMethodDescriptor<String, String> cmd = ClientMethodDescriptor.builder("foo", md).build();
        assertThat(cmd.name(), equalTo("foo"));
        assertThat(cmd.descriptor().getFullMethodName(), equalTo("EchoService/foo"));

        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));

    }

    @Test
    public void testMarshallerTypesForProtoAndNonProtoBuilder() {
        ClientServiceDescriptor stringSvcDesc2 = ClientServiceDescriptor
                .builder(StringService.class, StringServiceGrpc.getServiceDescriptor())
                .build();

        ClientMethodDescriptor<StringMessage, StringMessage> toLowerDesc
                = stringSvcDesc2.method("Lower");

        assertThat(toLowerDesc.name(), equalTo("Lower"));
        assertThat(toLowerDesc.descriptor().getFullMethodName(), equalTo("StringService/Lower"));

        assertThat(toLowerDesc.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo("io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller"));
        assertThat(toLowerDesc.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo("io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller"));

        // Now create a new Desc from the toLower descriptor
        ClientMethodDescriptor<String, String> cmd = toLowerDesc.toBuilder()
                .name("convert")
                .requestType(String.class)
                .build();

        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));
        assertThat(toLowerDesc.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo("io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller"));

        cmd = cmd.toBuilder().responseType(Date.class).build();
        assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));
        assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(),
                   equalTo(JavaMarshaller.class.getName()));

    }
    @Test
    public void testNonProtoBuilderWithToBuilder() {
        io.grpc.MethodDescriptor grpcDescriptor = EchoServiceGrpc.getServiceDescriptor()
                .getMethods()
                .stream()
                .filter(md -> md.getFullMethodName().equals("EchoService/Echo"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find echo method"));

        Context.Key<String> key = Context.key("test");
        ClientMethodDescriptor cmd = ClientMethodDescriptor.builder("Echo", grpcDescriptor)
                .counted()
                .addContextKey(Context.key("K1"), "V1")
                .requestType(String.class)
                .responseType(String.class)
                .build();

        ClientMethodDescriptor cmd2 = cmd.toBuilder().build();

        assertThat(cmd2, is(notNullValue()));
        assertThat(cmd.name(), equalTo(cmd2.name()));
        assertThat(cmd.name(), equalTo(cmd2.name()));
        assertThat(cmd.metricType(), equalTo(cmd2.metricType()));
        assertThat(cmd.requestType(), equalTo(cmd2.requestType()));
        assertThat(cmd.responseType(), equalTo(cmd2.responseType()));

        assertThat(cmd.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(cmd), is(true));
        assertThat(cmd2.isIdentical(cmd2), is(true));
        assertThat(cmd.isIdentical(new Date()), is(false));
        assertThat(cmd2.isIdentical(new Date()), is(false));
        assertThat(cmd != cmd2, is(true));
    }


    @Test
    public void testMethodDescriptorCreation() {
        for (MethodDescriptor.MethodType type : MethodDescriptor.MethodType.values()) {
            String methodName = "get";
            String fullName = "TreeService/" + methodName;
            ClientMethodDescriptor<Integer, String> cmd = ClientMethodDescriptor.unary(fullName, Integer.class, String.class);
            switch (type) {
            case UNARY:
                cmd = ClientMethodDescriptor.unary(fullName, Integer.class, String.class);
                break;
            case CLIENT_STREAMING:
                cmd = ClientMethodDescriptor.clientStreaming(fullName, Integer.class, String.class);
                break;
            case SERVER_STREAMING:
                cmd = ClientMethodDescriptor.serverStreaming(fullName, Integer.class, String.class);
                break;
            case BIDI_STREAMING:
                cmd = ClientMethodDescriptor.bidirectional(fullName, Integer.class, String.class);
                break;
            }


            if (type == MethodDescriptor.MethodType.UNKNOWN) {
                continue;
            }

            assertThat(cmd.descriptor().getFullMethodName(), equalTo(fullName));
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getType(), equalTo(type));
            assertThat(cmd.metricType(), nullValue());

            // Check Marshallers
            assertThat(cmd.descriptor().getRequestMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
            assertThat(cmd.descriptor().getResponseMarshaller().getClass().getName(), equalTo(JavaMarshaller.class.getName()));
        }
    }
}
