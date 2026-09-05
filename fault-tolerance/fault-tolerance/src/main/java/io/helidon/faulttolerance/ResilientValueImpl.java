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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

final class ResilientValueImpl<T> implements ResilientValue<T> {
    private static final System.Logger LOGGER = System.getLogger(ResilientValue.class.getName());

    private final String description;
    private final Supplier<T> loader;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final AtomicReference<LazyValue<Outcome<T>>> attempt;

    private volatile T value;
    private volatile boolean loaded;

    ResilientValueImpl(String description,
                       Supplier<T> loader,
                       RetryConfig retryConfig,
                       CircuitBreakerConfig circuitBreakerConfig) {
        this.description = requireDescription(description);
        this.loader = Objects.requireNonNull(loader);
        this.retry = Retry.create(normalize(this.description, Objects.requireNonNull(retryConfig)));
        this.circuitBreaker = CircuitBreaker.create(normalize(this.description, Objects.requireNonNull(circuitBreakerConfig)));
        this.attempt = new AtomicReference<>(newAttempt());
    }

    @Override
    public T get() {
        if (loaded) {
            return value;
        }

        LazyValue<Outcome<T>> currentAttempt = attempt.get();
        Outcome<T> outcome = currentAttempt.get();
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

    private static RetryConfig normalize(String description, RetryConfig config) {
        return RetryConfig.builder(config)
                .clearApplyOn()
                .addApplyOn(UnavailableException.class)
                .clearSkipOn()
                .name(description + "-retry")
                .buildPrototype();
    }

    private static CircuitBreakerConfig normalize(String description, CircuitBreakerConfig config) {
        return CircuitBreakerConfig.builder(config)
                .clearApplyOn()
                .addApplyOn(UnavailableException.class)
                .clearSkipOn()
                .name(description + "-circuit-breaker")
                .buildPrototype();
    }

    private static String requireDescription(String description) {
        Objects.requireNonNull(description);
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }
        return description;
    }

    private LazyValue<Outcome<T>> newAttempt() {
        return LazyValue.create(() -> {
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
            return retry.invoke(loader);
        } catch (RetryTimeoutException e) {
            throw new ResilientValue.UnavailableException(description + " did not become available before the retry timeout", e);
        }
    }

    private record Outcome<T>(T value, Throwable failure) {
    }
}
