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

import java.util.function.Supplier;

import io.helidon.common.Api;

/**
 * Configuration of a {@link ResilientValue}.
 *
 * @param <T> type of the loaded value
 */
@Api.Internal
public interface ResilientValueConfig<T> {
    /*
     * This type intentionally has no builder. It is an internal contract whose callers provide private implementations,
     * keeping it extensible and allowing a future replacement with a Blueprint-generated type. This is an
     * architect-approved exception to Helidon development guideline Rule 8.3.
     */

    /**
     * Safe description of the value, used in messages and logs.
     *
     * @return value description
     */
    String description();

    /**
     * Supplier that loads the value.
     *
     * @return value loader
     */
    Supplier<T> loader();

    /**
     * Retry handler.
     *
     * @return retry handler
     */
    Retry retry();

    /**
     * Circuit breaker handler.
     *
     * @return circuit breaker handler
     */
    CircuitBreaker circuitBreaker();

    /**
     * Timeout handler applied to each load attempt.
     *
     * @return timeout handler
     */
    Timeout timeout();
}
