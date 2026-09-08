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

package io.helidon.faulttolerance;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class ResilientValueImpl<T> implements ResilientValue<T> {
    private static final System.Logger LOGGER = System.getLogger(ResilientValue.class.getName());

    private final String description;
    private final Supplier<T> loader;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Timeout timeout;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicReference<FutureTask<Outcome<T>>> attempt;

    private volatile T value;
    private volatile boolean loaded;

    ResilientValueImpl(String description,
                       Supplier<T> loader,
                       Retry retry,
                       CircuitBreaker circuitBreaker,
                       Timeout timeout) {
        this.description = requireDescription(description);
        this.loader = Objects.requireNonNull(loader);
        this.retry = Objects.requireNonNull(retry);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
        this.timeout = Objects.requireNonNull(timeout);
        validateTimeout(retry.prototype().overallTimeout(), timeout.prototype().timeout());
        if (!timeout.prototype().currentThread()) {
            throw new IllegalArgumentException("Timeout must execute on the current thread");
        }
        this.attempt = new AtomicReference<>(newAttempt());
    }

    @Override
    public T get() {
        if (loaded) {
            return value;
        }

        FutureTask<Outcome<T>> currentAttempt = attempt.get();
        currentAttempt.run();
        Outcome<T> outcome;
        try {
            outcome = currentAttempt.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupplierException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unexpected failure loading " + description, e.getCause());
        }
        Throwable failure = outcome.failure();
        if (failure == null) {
            return outcome.value();
        }

        attempt.compareAndSet(currentAttempt, newAttempt());
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked failure loading " + description, failure);
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    private static String requireDescription(String description) {
        Objects.requireNonNull(description);
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }
        return description;
    }

    private static void validateTimeout(Duration retryTimeout, Duration attemptTimeout) {
        if (attemptTimeout.isNegative() || attemptTimeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (attemptTimeout.compareTo(retryTimeout) > 0) {
            throw new IllegalArgumentException("Timeout must not exceed retry overall timeout");
        }
    }

    private FutureTask<Outcome<T>> newAttempt() {
        return new FutureTask<>(() -> {
            try {
                return new Outcome<>(load(), null);
            } catch (RuntimeException | Error e) {
                return new Outcome<>(null, e);
            }
        });
    }

    private T load() {
        T loadedValue;
        try {
            loadedValue = circuitBreaker.invoke(this::loadWithRetry);
        } catch (CircuitBreakerOpenException e) {
            throw new ResilientValue.UnavailableException(description + " is temporarily unavailable", e);
        } catch (UnavailableException e) {
            failed.set(true);
            LOGGER.log(Level.WARNING, "{0} is unavailable; retries are exhausted: {1}", description, e.getMessage());
            throw new ResilientValue.UnavailableException(description + " is temporarily unavailable", e);
        }
        value = Objects.requireNonNull(loadedValue, "The loader for " + description + " returned null");
        loaded = true;
        if (failed.compareAndSet(true, false)) {
            LOGGER.log(Level.INFO, "{0} is available again", description);
        }
        return loadedValue;
    }

    private T loadWithRetry() {
        try {
            return retry.invoke(this::loadWithTimeout);
        } catch (RetryTimeoutException e) {
            throw new ResilientValue.UnavailableException(description + " did not become available before the retry timeout", e);
        }
    }

    private T loadWithTimeout() {
        try {
            return timeout.invoke(loader);
        } catch (TimeoutException e) {
            throw new ResilientValue.UnavailableException(description + " load attempt timed out", e);
        }
    }

    private record Outcome<T>(T value, Throwable failure) {
    }
}
