/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.graphql.server;

import java.util.Optional;

/**
 * GraphQL execution context to support partial results and context values.
 */
public interface ExecutionContext {
    /**
     * Set a context value.
     *
     * @param name name of the context value
     * @param value context value
     */
    void setContextValue(String name, Object value);

    /**
     * Retrieve a context value.
     *
     * @param name name of the context value
     * @return the context value, or empty if it is not present
     */
    Optional<Object> contextValue(String name);

    /**
     * Retrieve a typed context value.
     *
     * @param name name of the context value
     * @param type expected type of the context value
     * @param <T> type of the context value
     * @return the context value, or empty if it is not present
     * @throws ClassCastException in case the context value is not assignable to the requested type
     */
    <T> Optional<T> contextValue(String name, Class<T> type);

    /**
     * Add a partial results {@link Throwable}.
     *
     * @param throwable {@link Throwable}
     */
    void partialResultsException(Throwable throwable);

    /**
     * Retrieve partial results {@link Throwable}.
     *
     * @return the {@link Throwable}
     */
    Throwable partialResultsException();

    /**
     * Whether an exception was set on this context.
     *
     * @return true if there was a partial results exception
     */
    boolean hasPartialResultsException();
}
