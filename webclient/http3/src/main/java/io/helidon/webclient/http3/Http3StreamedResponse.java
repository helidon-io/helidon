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

package io.helidon.webclient.http3;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Headers;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.ResolvedClientTarget;

final class Http3StreamedResponse implements ReleasableResource {
    private final Http3MessageReader.ResponseHead responseHead;
    private final ResolvedClientTarget resolvedTarget;
    private final Instant receivedAt;
    private final CompletableFuture<Headers> trailers;
    private final Http3RequestStream requestStream;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean entityFullyRead;
    private volatile InputStream inputStream;

    Http3StreamedResponse(Http3MessageReader.ResponseHead responseHead,
                          ResolvedClientTarget resolvedTarget,
                          Instant receivedAt,
                          CompletableFuture<Headers> trailers,
                          InputStream inputStream,
                          Http3RequestStream requestStream,
                          boolean hasEntity) {
        this.responseHead = Objects.requireNonNull(responseHead, "responseHead");
        this.resolvedTarget = Objects.requireNonNull(resolvedTarget, "resolvedTarget");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.trailers = Objects.requireNonNull(trailers, "trailers");
        this.inputStream = inputStream;
        this.requestStream = Objects.requireNonNull(requestStream, "requestStream");
        this.entityFullyRead = new AtomicBoolean(!hasEntity);
    }

    int status() {
        return responseHead.status().code();
    }

    Headers headers() {
        return responseHead.headers();
    }

    ResolvedClientTarget resolvedTarget() {
        return resolvedTarget;
    }

    Instant receivedAt() {
        return receivedAt;
    }

    CompletableFuture<Headers> trailers() {
        return trailers;
    }

    InputStream inputStream() {
        return inputStream;
    }

    boolean hasEntity() {
        return inputStream != null;
    }

    void inputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    void entityFullyRead() {
        entityFullyRead.set(true);
    }

    void readTimedOut(Throwable failure) {
        if (closed.compareAndSet(false, true)) {
            requestStream.responseReadTimedOut(failure, trailers);
        }
    }

    @Override
    public void closeResource() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        boolean completed = entityFullyRead.get();
        RuntimeException cleanupFailure = null;
        if (!completed) {
            try {
                requestStream.cancelRequestBody();
            } catch (RuntimeException e) {
                cleanupFailure = e;
            }
            try {
                requestStream.cancelResponseBody();
            } catch (RuntimeException e) {
                if (cleanupFailure == null) {
                    cleanupFailure = e;
                } else {
                    Http3RequestFailureSupport.addSuppressed(cleanupFailure, e);
                }
            }
            trailers.completeExceptionally(
                    new IllegalStateException("HTTP/3 response closed before trailers were read."));
        }
        try {
            requestStream.finish(completed ? StreamOutcome.COMPLETED : StreamOutcome.CANCELLED);
        } catch (RuntimeException e) {
            if (cleanupFailure == null) {
                cleanupFailure = e;
            } else {
                Http3RequestFailureSupport.addSuppressed(cleanupFailure, e);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }
}
