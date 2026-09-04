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

package io.helidon.http.http3;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;

/**
 * Owned observation of a remote HTTP/3 unidirectional stream.
 *
 * <p>The caller must close this handle when its connection owner stops observing the stream.
 */
@Api.Internal
public final class Http3StreamObservation implements AutoCloseable {
    private final CompletableFuture<Void> future;
    private final CompletionStage<Void> completion;

    Http3StreamObservation(CompletableFuture<Void> future) {
        this.future = Objects.requireNonNull(future, "future");
        this.completion = future.minimalCompletionStage();
    }

    /**
     * Completion of this observation. The stage completes normally at stream end, exceptionally when observation fails,
     * and with cancellation when the owner closes this handle. The returned stage cannot be used to complete or cancel
     * the observation.
     *
     * @return observation completion
     */
    public CompletionStage<Void> completion() {
        return completion;
    }

    /**
     * Cancels this observation, wakes any blocked reader, and releases the stream reader.
     *
     * <p>This operation is idempotent.
     */
    @Override
    public void close() {
        future.cancel(false);
    }
}
