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

package io.helidon.webclient.grpc;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.EmptyProto;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class GrpcClientCallLifecycleTest {
    private static final Descriptors.FileDescriptor PROTO;

    static {
        DescriptorProtos.MethodDescriptorProto method = DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName("Wait")
                .setInputType(".google.protobuf.Empty")
                .setOutputType(".google.protobuf.Empty")
                .setClientStreaming(true)
                .setServerStreaming(true)
                .build();
        DescriptorProtos.ServiceDescriptorProto service = DescriptorProtos.ServiceDescriptorProto.newBuilder()
                .setName("Lifecycle")
                .addMethod(method)
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("lifecycle.proto")
                .setPackage("lifecycle")
                .addDependency("google/protobuf/empty.proto")
                .addService(service)
                .build();
        try {
            PROTO = Descriptors.FileDescriptor.buildFrom(file,
                                                         new Descriptors.FileDescriptor[] {EmptyProto.getDescriptor()});
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void resetAfterUnboundedDemandClosesCall() throws Exception {
        WebServer server = startServer();
        GrpcClientCall<Empty, Empty> call = newCall(server, Duration.ZERO);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Status> closeStatus = new AtomicReference<>();
        try {
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    closeStatus.set(status);
                    closed.countDown();
                }
            }, new Metadata());
            call.request(Integer.MAX_VALUE);

            call.clientStream().rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));

            assertThat("call closed", closed.await(10, TimeUnit.SECONDS), is(true));
            assertThat(closeStatus.get().getCode(), is(Status.Code.CANCELLED));
        } finally {
            call.cancel("test complete", null);
            server.stop();
        }
    }

    @Test
    void cancellationTerminatesReadTaskAndHeartbeatTimer() throws Exception {
        WebServer server = startServer();
        GrpcClientCall<Empty, Empty> call = newCall(server, Duration.ofHours(1));
        CountDownLatch closed = new CountDownLatch(1);
        try {
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    closed.countDown();
                }
            }, new Metadata());
            call.request(1);
            call.sendMessage(Empty.getDefaultInstance());
            call.halfClose();

            assertThat("heartbeat scheduled", call.heartbeatTaskPending(), is(true));

            call.cancel("test complete", null);

            assertThat("call closed", closed.await(10, TimeUnit.SECONDS), is(true));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!call.readTaskTerminated()) {
                    Thread.sleep(10);
                }
            });
            assertThat("heartbeat cancelled", call.heartbeatTaskCancelled(), is(true));
        } finally {
            server.stop();
        }
    }

    @Test
    void negativeHeartbeatPeriodIsDisabled() throws Exception {
        WebServer server = startServer();
        GrpcClientCall<Empty, Empty> call = newCall(server, Duration.ofSeconds(-1));
        CountDownLatch closed = new CountDownLatch(1);
        try {
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    closed.countDown();
                }
            }, new Metadata());
            call.request(1);
            call.halfClose();

            assertThat("heartbeat not scheduled", call.heartbeatTaskPending(), is(false));

            call.cancel("test complete", null);
            assertThat("call closed", closed.await(10, TimeUnit.SECONDS), is(true));
        } finally {
            server.stop();
        }
    }

    @Test
    void callerRunsHeartbeatDoesNotUseSchedulerThread() throws Exception {
        AtomicReference<String> rejectionThread = new AtomicReference<>();
        CountDownLatch rejected = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1,
                                                             1,
                                                             0,
                                                             TimeUnit.MILLISECONDS,
                                                             new SynchronousQueue<>(),
                                                             (task, ignored) -> {
                                                                 rejectionThread.compareAndSet(null,
                                                                                               Thread.currentThread()
                                                                                                       .getName());
                                                                 rejected.countDown();
                                                                 task.run();
                                                             });
        WebServer server = startServer();
        GrpcClient grpcClient = GrpcClient.builder()
                .executor(executor)
                .tls(tls -> tls.enabled(false))
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(config -> config.heartbeatPeriod(Duration.ofMillis(250)))
                .build();
        GrpcClientCall<Empty, Empty> call = newCall(grpcClient);
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch blockerRelease = new CountDownLatch(1);
        try {
            call.start(new ClientCall.Listener<>() { }, new Metadata());
            call.request(1);
            call.halfClose();
            assertThat("heartbeat scheduled", call.heartbeatTaskPending(), is(true));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (executor.getActiveCount() != 0) {
                    Thread.sleep(10);
                }
            });
            executor.execute(() -> {
                blockerEntered.countDown();
                try {
                    blockerRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat("executor saturated", blockerEntered.await(10, TimeUnit.SECONDS), is(true));

            assertThat("heartbeat dispatched to rejected executor", rejected.await(10, TimeUnit.SECONDS), is(true));
            assertThat(rejectionThread.get().startsWith("helidon-grpc-heartbeat-dispatch-"), is(true));
        } finally {
            blockerRelease.countDown();
            call.cancel("test complete", null);
            executor.shutdownNow();
            server.stop();
        }
    }

    private static WebServer startServer() {
        return WebServer.builder()
                .tls(tls -> tls.enabled(false))
                .addRouting(GrpcRouting.builder()
                                    .<Empty, Empty>bidi(PROTO,
                                                       "lifecycle.Lifecycle",
                                                       "Wait",
                                                       (StreamObserver<Empty> observer) -> new StreamObserver<Empty>() {
                                                       @Override
                                                       public void onNext(Empty value) {
                                                       }

                                                       @Override
                                                       public void onError(Throwable throwable) {
                                                       }

                                                       @Override
                                                       public void onCompleted() {
                                                       }
                                                   }))
                .build()
                .start();
    }

    private static GrpcClientCall<Empty, Empty> newCall(WebServer server, Duration heartbeatPeriod) {
        GrpcClient grpcClient = GrpcClient.builder()
                .tls(tls -> tls.enabled(false))
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(config -> config.heartbeatPeriod(heartbeatPeriod))
                .build();
        return newCall(grpcClient);
    }

    @SuppressWarnings("unchecked")
    private static GrpcClientCall<Empty, Empty> newCall(GrpcClient grpcClient) {
        GrpcClientMethodDescriptor method = GrpcClientMethodDescriptor.bidirectional("lifecycle.Lifecycle", "Wait")
                .requestType(Empty.class)
                .responseType(Empty.class)
                .build();
        ClientCall<Empty, Empty> clientCall = grpcClient.channel()
                .newCall((io.grpc.MethodDescriptor<Empty, Empty>) (io.grpc.MethodDescriptor<?, ?>) method.descriptor(),
                         CallOptions.DEFAULT);
        return (GrpcClientCall<Empty, Empty>) clientCall;
    }
}
