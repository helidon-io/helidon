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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;

import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

class GrpcRoutingTest {

    @Test
    void testInterceptorsApplyToAllBuiltServices() {
        List<String> calls = new ArrayList<>();
        GrpcRouting routing = GrpcRouting.builder()
                .config(Config.empty())
                .service(service("first.Service"))
                .intercept(new RecordingInterceptor("first", calls))
                .service(service("second.Service"))
                .intercept(new RecordingInterceptor("second", calls))
                .service(service("third.Service"))
                .build();

        startCall(routing, "first.Service/Call");
        assertThat(calls, is(List.of("first", "second")));

        calls.clear();
        startCall(routing, "second.Service/Call");
        assertThat(calls, is(List.of("first", "second")));

        calls.clear();
        startCall(routing, "third.Service/Call");
        assertThat(calls, is(List.of("first", "second")));
    }

    @Test
    void testBuildReturnsFreshRouting() {
        GrpcRouting.Builder builder = GrpcRouting.builder()
                .config(Config.empty())
                .service(service("test.Service"));

        GrpcRouting first = builder.build();
        GrpcRouting second = builder.build();

        assertThat(second, not(sameInstance(first)));
    }

    private static ServerServiceDefinition service(String serviceName) {
        String fullMethodName = serviceName + "/Call";
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(methodDescriptor(fullMethodName),
                                              (call, headers) -> new ServerCall.Listener<>() {
                                              });
        return ServerServiceDefinition.builder(serviceName)
                .addMethod(definition)
                .build();
    }

    private static MethodDescriptor<String, String> methodDescriptor(String fullMethodName) {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void startCall(GrpcRouting routing, String fullMethodName) {
        GrpcRouteHandler<?, ?> route = routing.findRoute(HttpPrologue.create("HTTP/2.0",
                                                                             "HTTP",
                                                                             "2.0",
                                                                             Method.POST,
                                                                             "/" + fullMethodName,
                                                                             false));
        assertThat(route, notNullValue());
        ServerCallHandler handler = route.callHandler();
        handler.startCall(new TestServerCall(route.method()), new Metadata());
    }

    private record RecordingInterceptor(String name, List<String> calls) implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            calls.add(name);
            return next.startCall(call, headers);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class TestServerCall extends ServerCall {
        private final MethodDescriptor method;

        private TestServerCall(MethodDescriptor method) {
            this.method = method;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(Object message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor getMethodDescriptor() {
            return method;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }
    }
}
