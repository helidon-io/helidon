/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

/**
 * A Default Context to be supplied to {@link ExecutionContext}.
 */
public interface Context {

    /**
     * Add a partial results {@link Throwable}.
     *
     * @param throwable {@link Throwable}
     */
    void addPartialResultsException(Throwable throwable);

    /**
     * Retrieve partial results {@link Throwable}.
     *
     * @return the {@link Throwable}
     */
    Throwable getPartialResultsException();

    /**
     * Remove partial results {@link Throwable}.
     */
    void removePartialResultsException();
}
