/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.api;

/**
 * Invocation of an element that has parameters, and may throw checked exceptions.
 * This type is used to handle intercepted methods, constructors, and field injections.
 *
 * @param <T> type of the result of the invocation
 */
@FunctionalInterface
public interface Invoker<T> {
    /**
     * Invoke the element.
     *
     * @param parameters to pass to the element
     * @return result of the invocation
     * @throws Exception any exception that may be required by the invoked element
     */
    T invoke(Object... parameters) throws Exception;
}
