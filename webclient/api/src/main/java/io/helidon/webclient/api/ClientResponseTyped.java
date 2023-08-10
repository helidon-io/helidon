/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

/**
 * Http client response explicitly typed.
 * The typed entity does not extend {@link java.lang.AutoCloseable}, as in most cases the entity is fully read into memory.
 * Kindly make sure that you fully consume the entity in cases where it cannot be buffered (depends on media support used).
 *
 * @param <T> type of the response
 */
public interface ClientResponseTyped<T> extends ClientResponseBase {
    /**
     * Entity of the requested type.
     *
     * @return entity
     */
    T entity();

    /**
     * Closes the response.
     * This may have no impact on the underlying connection.
     * Response is implicitly closed if the entity is fully read.
     */
    void close();
}
