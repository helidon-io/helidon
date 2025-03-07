/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
package io.helidon.tracing;

/**
 * A Scope that can be (eventually) closed. Used when making a span active.
 */
public interface Scope extends AutoCloseable {
    @Override
    void close();

    /**
     * Whether the method {@link #close()} was already called or not.
     * @return if this scope is closed
     */
    boolean isClosed();

    /**
     * Access the underlying scope by specific type.
     * This is a dangerous operation that will succeed only if the scope is of expected type. This practically
     * removes abstraction capabilities of this API.
     *
     * @param scopeClass type to access
     * @param <T>        type of the scope
     * @return instance of the scope
     * @throws java.lang.IllegalArgumentException in case the scope cannot provide the expected type
     */
    default <T> T unwrap(Class<T> scopeClass) {
        try {
            return scopeClass.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("This scope is not compatible with " + scopeClass.getName());
        }
    }
}
