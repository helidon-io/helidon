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
package io.helidon.common;

/**
 * Behavior of a type that wraps a related type, typically through delegation.
 */
@Api.Stable
public interface Wrapper {

    /**
     * Unwraps the delegate as the specified type.
     *
     * @param type {@link Class} to which to cast the delegate
     * @param <T>  type to cast to
     * @return the delegate cast as the requested type
     * @throws ClassCastException if the delegate is not compatible with the requested type
     */
    <T> T unwrap(Class<? extends T> type);
}
