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

package io.helidon.declarative.tests.grpc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webserver.grpc.GrpcEntryPoint;
import io.helidon.webserver.grpc.GrpcMethodDescriptor;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Generated unary response bindings, without transport or serialization costs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class UnaryResponseJmhBenchmark {
    private final Metadata headers = new Metadata();

    @Param({"direct", "optionalPresent", "optionalEmpty", "directNull", "optionalNull"})
    public String scenario;

    private GreetingRequest request;
    private Status.Code expectedStatus;
    private ServerCallHandler<GreetingRequest, GreetingReply> handler;
    private RecordingCall call;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() {
        var invocation = switch (scenario) {
            case "direct" -> new Invocation("DirectGreet", "default", Status.Code.OK);
            case "optionalPresent" -> new Invocation("OptionalGreet", "default", Status.Code.OK);
            case "optionalEmpty" -> new Invocation("OptionalGreet", "empty", Status.Code.NOT_FOUND);
            case "directNull" -> new Invocation("DirectGreet", "null", Status.Code.INTERNAL);
            case "optionalNull" -> new Invocation("OptionalGreet", "null", Status.Code.INTERNAL);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
        var registration = new GreetingEndpoint__GrpcRegistration(new GreetingEndpoint(), new NoInterceptors());
        var method = (GrpcMethodDescriptor<GreetingRequest, GreetingReply>) registration.descriptor()
                .method(invocation.method());
        handler = method.callHandler();
        call = new RecordingCall(method.descriptor());
        request = GreetingRequest.newBuilder().setName(invocation.request()).build();
        expectedStatus = invocation.status();
    }

    @Benchmark
    public void unary(Blackhole blackhole) {
        call.response = null;
        call.status = null;
        call.responses = 0;
        call.completions = 0;
        var listener = handler.startCall(call, headers);
        listener.onMessage(request);
        listener.onHalfClose();
        blackhole.consume(call.response);
        blackhole.consume(call.status);
    }

    @TearDown(Level.Iteration)
    public void verifyResponse() {
        assertThat("Completion count for " + scenario, call.completions, is(1));
        assertThat("Status for " + scenario, call.status.getCode(), is(expectedStatus));
        if (expectedStatus == Status.Code.OK) {
            assertThat("Response count for " + scenario, call.responses, is(1));
            assertThat("Response for " + scenario, call.response, sameInstance(GreetingReply.getDefaultInstance()));
        } else {
            assertThat("Response count for " + scenario, call.responses, is(0));
            assertThat("Response for " + scenario, call.response, nullValue());
        }
    }

    private record Invocation(String method, String request, Status.Code status) {
    }

    private static class NoInterceptors implements GrpcEntryPoint.EntryPoints {
        @Override
        public boolean hasInterceptors() {
            return false;
        }

        @Override
        public ServerInterceptor interceptor(ServiceDescriptor<?> descriptor,
                                             Set<Qualifier> typeQualifiers,
                                             List<Annotation> typeAnnotations,
                                             TypedElementInfo methodInfo) {
            throw new UnsupportedOperationException("No benchmark interceptors");
        }
    }

    private static class RecordingCall extends ServerCall<GreetingRequest, GreetingReply> {
        private final MethodDescriptor<GreetingRequest, GreetingReply> descriptor;
        private GreetingReply response;
        private Status status;
        private int responses;
        private int completions;

        private RecordingCall(MethodDescriptor<GreetingRequest, GreetingReply> descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public void request(int count) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(GreetingReply message) {
            response = message;
            responses++;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            this.status = status;
            completions++;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<GreetingRequest, GreetingReply> getMethodDescriptor() {
            return descriptor;
        }
    }
}
