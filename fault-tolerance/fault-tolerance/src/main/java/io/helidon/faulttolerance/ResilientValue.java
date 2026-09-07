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

import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.Api;

/**
 * A value loaded on first use using a timeout and retry protected by a circuit breaker.
 * Each loader invocation is guarded by the timeout, the retry repeats timed attempts, and the circuit breaker wraps
 * the complete retry batch.
 * The timeout must execute on the current thread so a retry cannot overlap a timed-out loader that is still
 * unwinding. The timeout interrupts the loader at its deadline; interruption-aware or independently bounded I/O is
 * required for prompt termination.
 * A successfully loaded value is cached permanently. Failed loads may be tried again according to the configured
 * circuit breaker. Concurrent callers wait for the active load attempt and share its result.
 *
 * @param <T> type of the loaded value
 */
@Api.Internal
public interface ResilientValue<T> extends Supplier<T> {
    /**
     * Create a resilient value using the provided fault tolerance handlers.
     * Fault tolerance state belongs to the supplied handlers; callers should supply dedicated instances when state
     * must not be shared with other operations.
     *
     * @param description safe description of the value, used in messages and logs
     * @param loader supplier that loads the value
     * @param retry retry handler
     * @param circuitBreaker circuit breaker handler
     * @param timeout timeout handler applied to each load attempt
     * @param <T> type of the loaded value
     * @return a new resilient value
     * @throws IllegalArgumentException if the timeout is not positive, exceeds the retry overall timeout, or does not
     *                                  execute on the current thread
     */
    static <T> ResilientValue<T> create(String description,
                                        Supplier<T> loader,
                                        Retry retry,
                                        CircuitBreaker circuitBreaker,
                                        Timeout timeout) {
        return new ResilientValueImpl<>(description, loader, retry, circuitBreaker, timeout);
    }

    /**
     * Whether the value has already been loaded successfully.
     *
     * @return {@code true} if the value is loaded
     */
    boolean isLoaded();

    /**
     * Exception indicating that a source may become available later.
     */
    @Api.Internal
    class UnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Create a new unavailable exception.
         *
         * @param message safe description of the failure
         */
        public UnavailableException(String message) {
            super(Objects.requireNonNull(message));
        }

        /**
         * Create a new unavailable exception.
         *
         * @param message safe description of the failure
         * @param cause cause of the failure
         */
        public UnavailableException(String message, Throwable cause) {
            super(Objects.requireNonNull(message), Objects.requireNonNull(cause));
        }
    }
}
