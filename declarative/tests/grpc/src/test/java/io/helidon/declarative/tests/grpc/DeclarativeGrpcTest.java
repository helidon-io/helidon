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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcReflectionFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import com.google.protobuf.DescriptorProtos;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class DeclarativeGrpcTest {
    private static final String USERNAME = "tomas";
    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private static final char[] PASSWORD = "changeit".toCharArray();

    private final ManagedChannel channel;
    private final GreetingServiceGrpc.GreetingServiceBlockingStub blockingStub;
    private final GreetingServiceGrpc.GreetingServiceStub asyncStub;
    private final TestSpanExporter exporter;

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.addFeature(GrpcReflectionFeature.builder().enabled(true).build());
    }

    DeclarativeGrpcTest(WebServer server, TestTracerFactory tracerFactory) {
        this.channel = ManagedChannelBuilder.forAddress("localhost", server.port())
                .usePlaintext()
                .build();
        this.blockingStub = GreetingServiceGrpc.newBlockingStub(channel);
        this.asyncStub = GreetingServiceGrpc.newStub(channel);
        this.exporter = tracerFactory.exporter();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        channel.shutdown();
        assertThat("gRPC channel terminated",
                   channel.awaitTermination(10, TimeUnit.SECONDS),
                   is(true));
    }

    @Test
    void testUnary() {
        var response = blockingStub.greet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testUnaryMethodMetrics() {
        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry();
        List<Tag> methodTags = List.of(Tag.create("transport", "grpc"), Tag.create("scope", "application"));
        List<Tag> methodTimerTags = List.of(Tag.create("scope", "application"));
        List<Tag> grpcMethodTags = List.of(Tag.create("grpc.method", "GreetingService/Greet"),
                                           Tag.create("scope", "vendor"));
        List<Tag> grpcDurationTags = List.of(Tag.create("grpc.method", "GreetingService/Greet"),
                                             Tag.create("grpc.status", "OK"),
                                             Tag.create("scope", "vendor"));
        long methodCounterBefore = counterCount(meterRegistry, "GreetingEndpoint.greet", methodTags);
        long methodTimerBefore = timerCount(meterRegistry, "grpc-greet", methodTimerTags);
        long grpcStartedBefore = counterCount(meterRegistry, "grpc.server.call.started", grpcMethodTags);
        long grpcDurationBefore = timerCount(meterRegistry, "grpc.server.call.duration", grpcDurationTags);

        var response = blockingStub.greet(request("Metrics"));

        assertThat(response.getMessage(), is("Hello Metrics"));
        assertThat(counterCount(meterRegistry, "GreetingEndpoint.greet", methodTags), is(methodCounterBefore + 1));
        assertThat(timerCount(meterRegistry, "grpc-greet", methodTimerTags), is(methodTimerBefore + 1));
        assertThat(counterCount(meterRegistry, "grpc.server.call.started", grpcMethodTags), is(grpcStartedBefore + 1));
        assertThat(timerCount(meterRegistry, "grpc.server.call.duration", grpcDurationTags), is(grpcDurationBefore + 1));
    }

    @Test
    void testUnaryMethodTracing() {
        exporter.clear();

        var response = blockingStub.greet(request("Tracing"));

        assertThat(response.getMessage(), is("Hello Tracing"));
        var spans = exporter.spanData(2);
        exporter.clear();
        SpanData grpcSpan = span(spans, "GreetingService/Greet");
        SpanData methodSpan = span(spans, "grpc-greet-method");

        assertThat("gRPC span", grpcSpan, notNullValue());
        assertThat("declarative method span", methodSpan, notNullValue());
        assertThat(methodSpan.getTraceId(), is(grpcSpan.getTraceId()));
        assertThat(methodSpan.getParentSpanId(), is(grpcSpan.getSpanId()));
        assertThat(methodSpan.getKind(), is(SpanKind.SERVER));
        assertThat(methodSpan.getAttributes().get(AttributeKey.stringKey("transport")), is("grpc"));
    }

    @Test
    void testProtoReflection() throws Exception {
        ServerReflectionResponse list = reflectionResponse(ServerReflectionRequest.newBuilder()
                                                                   .setListServices("*")
                                                                   .build());
        Set<String> serviceNames = list.getListServicesResponse()
                .getServiceList()
                .stream()
                .map(ServiceResponse::getName)
                .collect(Collectors.toSet());
        assertThat(serviceNames.toString(), serviceNames.contains("GreetingService"), is(true));

        for (String symbol : List.of("GreetingService", "GreetingService.Greet", "GreetingRequest")) {
            ServerReflectionResponse response = reflectionResponse(ServerReflectionRequest.newBuilder()
                                                                         .setFileContainingSymbol(symbol)
                                                                         .build());
            assertThat(response.getFileDescriptorResponse().getFileDescriptorProtoCount(), greaterThan(0));
        }

        ServerReflectionResponse file = reflectionResponse(ServerReflectionRequest.newBuilder()
                                                               .setFileByFilename("greeting.proto")
                                                               .build());
        var fileResponse = file.getFileDescriptorResponse();
        assertThat(fileResponse.getFileDescriptorProtoCount(), is(1));
        DescriptorProtos.FileDescriptorProto descriptor =
                DescriptorProtos.FileDescriptorProto.parseFrom(fileResponse.getFileDescriptorProto(0));
        assertThat(descriptor.getName(), is("greeting.proto"));
        assertThat(descriptor.getService(0).getName(), is("GreetingService"));
    }

    @Test
    void testUnaryUsesServiceRegistryInvocation() {
        var response = blockingStub.interceptedGreet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas intercepted"));
    }

    @Test
    void testValidatedUnary() {
        GreetingRequestValidatorProvider.reset();

        var response = blockingStub.validatedGreet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas"));
        assertThat(GreetingRequestValidatorProvider.invocations(), is(1));

        GreetingRequestValidatorProvider.reset();

        var invalid = assertThrows(StatusRuntimeException.class, () -> blockingStub.validatedGreet(request(" ")));

        assertThat(invalid.getStatus().getCode(), is(Code.INVALID_ARGUMENT));
        assertThat(invalid.getStatus().getDescription(), containsString("name is blank"));
        assertThat(GreetingRequestValidatorProvider.invocations(), is(1));
    }

    @Test
    void testSecureUnary() {
        var request = request("Tomas");

        var unauthenticated = assertThrows(StatusRuntimeException.class, () -> blockingStub.secureGreet(request));
        assertThat(unauthenticated.getStatus().getCode(), is(Code.UNAUTHENTICATED));

        var response = authenticatedBlockingStub(USERNAME)
                .secureGreet(request);

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testAuthorizedUnary() {
        var request = request("Tomas");

        var unauthenticated = assertThrows(StatusRuntimeException.class, () -> blockingStub.authorizedGreet(request));
        assertThat(unauthenticated.getStatus().getCode(), is(Code.UNAUTHENTICATED));

        var response = authenticatedBlockingStub(USERNAME)
                .authorizedGreet(request);

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testRoleProtectedUnary() {
        var request = request("Tomas");

        var unauthenticated = assertThrows(StatusRuntimeException.class, () -> blockingStub.adminGreet(request));
        assertThat(unauthenticated.getStatus().getCode(), is(Code.UNAUTHENTICATED));

        var wrongRole = assertThrows(StatusRuntimeException.class,
                                     () -> authenticatedBlockingStub(USER).adminGreet(request));
        assertThat(wrongRole.getStatus().getCode(), is(Code.PERMISSION_DENIED));

        var response = authenticatedBlockingStub(ADMIN)
                .adminGreet(request);

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testPermitAllUnary() {
        var response = blockingStub.permitAllGreet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testDenyAllUnary() {
        var denied = assertThrows(StatusRuntimeException.class, () -> blockingStub.denyAllGreet(request("Tomas")));

        assertThat(denied.getStatus().getCode(), is(Code.PERMISSION_DENIED));
    }

    @Test
    void testRoleValidatorUnary() {
        var request = request("Tomas");

        var unauthenticated = assertThrows(StatusRuntimeException.class,
                                           () -> blockingStub.roleValidatorGreet(request));
        assertThat(unauthenticated.getStatus().getCode(), is(Code.UNAUTHENTICATED));

        var wrongRole = assertThrows(StatusRuntimeException.class,
                                     () -> authenticatedBlockingStub(USER).roleValidatorGreet(request));
        assertThat(wrongRole.getStatus().getCode(), is(Code.PERMISSION_DENIED));

        var response = authenticatedBlockingStub(ADMIN)
                .roleValidatorGreet(request);

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testAuditedUnary() {
        var response = blockingStub.auditedGreet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testServerStreaming() {
        var responses = blockingStub.split(request("Tomas,Helidon"));
        var replies = new ArrayList<DeclarativeGrpcProto.GreetingReply>();
        responses.forEachRemaining(replies::add);

        assertThat(replies,
                   contains(reply("Hello Tomas"), reply("Hello Helidon")));
    }

    @Test
    void testClientStreaming() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        var replies = new ArrayList<DeclarativeGrpcProto.GreetingReply>();
        StreamObserver<GreetingRequest> requests = asyncStub.join(new StreamObserver<>() {
            @Override
            public void onNext(DeclarativeGrpcProto.GreetingReply response) {
                replies.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        });

        requests.onNext(request("Tomas"));
        requests.onNext(request("Helidon"));
        requests.onCompleted();

        assertThat("client stream completed",
                   completed.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(error.get(), nullValue());
        assertThat(replies, contains(reply("Hello Tomas, Helidon")));
    }

    @Test
    void testBidirectionalStreaming() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        var replies = new ArrayList<DeclarativeGrpcProto.GreetingReply>();
        StreamObserver<GreetingRequest> requests = asyncStub.chat(new StreamObserver<>() {
            @Override
            public void onNext(DeclarativeGrpcProto.GreetingReply response) {
                replies.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        });

        requests.onNext(request("Tomas"));
        requests.onNext(request("Helidon"));
        requests.onCompleted();

        assertThat("bidirectional stream completed",
                   completed.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(error.get(), nullValue());
        assertThat(replies,
                   contains(reply("Hello Tomas"), reply("Hello Helidon")));
    }

    private static GreetingRequest request(String name) {
        return GreetingRequest.newBuilder()
                .setName(name)
                .build();
    }

    private static DeclarativeGrpcProto.GreetingReply reply(String message) {
        return DeclarativeGrpcProto.GreetingReply.newBuilder()
                .setMessage(message)
                .build();
    }

    private GreetingServiceGrpc.GreetingServiceBlockingStub authenticatedBlockingStub(String username) {
        String token = username + ":" + new String(PASSWORD);
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        return blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static long counterCount(MeterRegistry meterRegistry, String name, List<Tag> tags) {
        return meterRegistry.counter(name, tags)
                .map(Counter::count)
                .orElse(0L);
    }

    private static long timerCount(MeterRegistry meterRegistry, String name, List<Tag> tags) {
        return meterRegistry.timer(name, tags)
                .map(Timer::count)
                .orElse(0L);
    }

    private static SpanData span(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(it -> it.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private ServerReflectionResponse reflectionResponse(ServerReflectionRequest request) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc.newStub(channel)
                .serverReflectionInfo(new StreamObserver<>() {
                    @Override
                    public void onNext(ServerReflectionResponse serverReflectionResponse) {
                        response.set(serverReflectionResponse);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                        completed.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        completed.countDown();
                    }
                });

        requests.onNext(request);
        requests.onCompleted();

        assertThat("reflection request completed",
                   completed.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(error.get(), nullValue());
        assertThat(response.get(), notNullValue());
        return response.get();
    }

}
