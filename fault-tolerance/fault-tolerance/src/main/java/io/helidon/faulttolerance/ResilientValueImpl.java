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
import java.util.function.Supplier;

final class ResilientValueImpl<T> implements ResilientValue<T> {
    private static final System.Logger LOGGER = System.getLogger(ResilientValue.class.getName());

    private final String description;
    private final Supplier<T> loader;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();

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
    }

    @Override
    public T get() {
        if (loaded) {
            return value;
        }
        if (!loading.compareAndSet(false, true)) {
            if (loaded) {
                return value;
            }
            throw ResilientValue.unavailable(description + " is already being loaded");
        }

        try {
            if (loaded) {
                return value;
            }
            T loadedValue;
            try {
                loadedValue = circuitBreaker.invoke(this::loadWithRetry);
            } catch (CircuitBreakerOpenException e) {
                throw ResilientValue.unavailable(description + " is temporarily unavailable", e);
            } catch (UnavailableException e) {
                failed.set(true);
                LOGGER.log(Level.WARNING, "{0} is unavailable; retries are exhausted: {1}", description, e.getMessage());
                throw ResilientValue.unavailable(description + " is temporarily unavailable", e);
            }
            value = Objects.requireNonNull(loadedValue, "The loader for " + description + " returned null");
            loaded = true;
            if (failed.compareAndSet(true, false)) {
                LOGGER.log(Level.INFO, "{0} is available again", description);
            }
            return loadedValue;
        } finally {
            loading.set(false);
        }
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

    private T loadWithRetry() {
        try {
            return retry.invoke(loader);
        } catch (RetryTimeoutException e) {
            throw ResilientValue.unavailable(description + " did not become available before the retry timeout", e);
        }
    }
}
