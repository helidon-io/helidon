/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

/**
 * Behavior of a type that wraps a related type.
 */
public interface Wrapped {

    /**
     * Unwraps the meter registry as the specified type.
     *
     * @param c {@link Class} to which to cast this meter registry
     * @return the meter registry cast as the requested type
     * @param <R> type to cast to
     */
    default <R> R unwrap(Class<? extends R> c) {
        return c.cast(this);
    }
}
