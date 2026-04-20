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
package io.helidon.webserver.tests.grpc;

import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.Descriptors;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.slow.Slow;
import io.helidon.webserver.grpc.slow.Slow.SlowRequest;
import io.helidon.webserver.grpc.slow.Slow.SlowResponse;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.stub.StreamObserver;

/**
 * A test gRPC service that sleeps for a configurable duration before responding.
 * Used to verify deadline enforcement on both client and server side.
 *
 * <p>The last deadline seen by a handler is stored in {@link #lastSeenDeadline}
 * for test assertions. Reset it to {@code null} before each test that checks it.
 */
class SlowService implements GrpcService {

    /** The last deadline seen in Context by a handler invocation. Reset before each test. */
    static final AtomicReference<Deadline> lastSeenDeadline = new AtomicReference<>();

    @Override
    public Descriptors.FileDescriptor proto() {
        return Slow.getDescriptor();
    }

    @Override
    public void update(Routing router) {
        router.unary("Slow", this::slow)
                .serverStream("SlowStream", this::slowStream);
    }

    private void slow(SlowRequest request, StreamObserver<SlowResponse> observer) {
        // Capture deadline inside the handler where Context is set by ContextSettingServerInterceptor
        lastSeenDeadline.set(Context.current().getDeadline());
        try {
            Thread.sleep(request.getDelayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        observer.onNext(SlowResponse.newBuilder().setText(request.getText()).build());
        observer.onCompleted();
    }

    private void slowStream(SlowRequest request, StreamObserver<SlowResponse> observer) {
        lastSeenDeadline.set(Context.current().getDeadline());
        try {
            Thread.sleep(request.getDelayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        observer.onNext(SlowResponse.newBuilder().setText(request.getText()).build());
        observer.onCompleted();
    }
}
