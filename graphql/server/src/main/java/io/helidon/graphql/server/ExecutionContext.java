/*
 * Copyright (c) 2020,2021 Oracle and/or its affiliates.
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

import graphql.ExecutionInput;

/**
 * GraphQL execution context to support partial results.
 */
public interface ExecutionContext {
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

    /**
     * Add the {@link ExecutionInput}.
     *
     * @param input {@link ExecutionInput}
     */
    void setExecutionInput(ExecutionInput input);

    /**
     * Retrieve the {@link ExecutionInput}.
     *
     * @return the {@link ExecutionInput}
     */
    ExecutionInput executionInput();
}
