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

package io.helidon.webserver;

import java.util.concurrent.CompletionException;

final class LifecycleFailures {
    private LifecycleFailures() {
    }

    static Throwable add(Throwable failure, Throwable next) {
        if (next == null) {
            return normalize(failure);
        }
        Throwable unwrapped = normalize(next);
        if (failure == null) {
            return unwrapped;
        }
        failure = normalize(failure);
        if (failure == unwrapped) {
            return failure;
        }
        failure.addSuppressed(unwrapped);
        return failure;
    }

    static void throwIfFailed(Throwable failure, String message) {
        if (failure == null) {
            return;
        }
        throwIfFailedAsIllegalState(failure, message);
    }

    static void throwIfFailedAsIllegalState(Throwable failure, String message) {
        if (failure == null) {
            return;
        }
        Throwable unwrapped = normalize(failure);
        if (unwrapped instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(message, unwrapped);
    }

    static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }

    private static Throwable normalize(Throwable failure) {
        if (failure == null) {
            return null;
        }
        Throwable unwrapped = unwrap(failure);
        if (unwrapped == failure) {
            return failure;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            add(unwrapped, suppressed);
        }
        return unwrapped;
    }
}
