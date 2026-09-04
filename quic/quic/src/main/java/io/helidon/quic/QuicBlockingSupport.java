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

package io.helidon.quic;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class QuicBlockingSupport {
    private QuicBlockingSupport() {
    }

    static <T> T await(CompletionStage<T> stage,
                       boolean cancelOnInterrupt,
                       Runnable interruptedCleanup,
                       String operation) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(interruptedCleanup, "interruptedCleanup");
        Objects.requireNonNull(operation, "operation");
        var future = stage.toCompletableFuture();
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CancelAction cancel = cancelOnInterrupt ? future::cancel : _ -> false;
            Throwable cleanupFailure = cleanup(cancel, interruptedCleanup);
            var failure = new QuicException(operation + " interrupted", e);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } catch (ExecutionException e) {
            throwMapped(e.getCause());
            throw new AssertionError();
        } catch (CancellationException e) {
            throw new QuicException(operation + " canceled", e);
        }
    }

    static <T> T await(CompletionStage<T> stage,
                       Duration timeout,
                       Runnable incompleteCleanup,
                       String operation) {
        Objects.requireNonNull(stage, "stage");
        QuicPublicApiSupport.positiveNanosDuration(timeout, "timeout");
        Objects.requireNonNull(incompleteCleanup, "incompleteCleanup");
        Objects.requireNonNull(operation, "operation");
        var future = stage.toCompletableFuture();
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Throwable cleanupFailure = cleanup(future::cancel, incompleteCleanup);
            var failure = new QuicException(operation + " interrupted", e);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } catch (TimeoutException e) {
            Throwable cleanupFailure = cleanup(future::cancel, incompleteCleanup);
            var failure = new QuicException(operation + " timed out after " + timeout, e);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } catch (ExecutionException e) {
            throwMapped(e.getCause());
            throw new AssertionError();
        } catch (CancellationException e) {
            throw new QuicException(operation + " canceled", e);
        }
    }

    private static Throwable cleanup(CancelAction cancel, Runnable additionalCleanup) {
        Throwable failure = null;
        try {
            cancel.cancel(false);
        } catch (RuntimeException | Error e) {
            failure = e;
        }
        try {
            additionalCleanup.run();
        } catch (RuntimeException | Error e) {
            if (failure == null) {
                failure = e;
            } else if (failure != e) {
                failure.addSuppressed(e);
            }
        }
        return failure;
    }

    private static void throwMapped(Throwable failure) {
        Throwable mapped = QuicApplicationSession.publicFailure(failure);
        if (mapped instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) mapped;
    }

    @FunctionalInterface
    private interface CancelAction {
        boolean cancel(boolean mayInterruptIfRunning);
    }
}
