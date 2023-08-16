/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

/**
 * A typed fault tolerance handler, to allow combination
 * of multiple handlers without losing type information.
 *
 * @param <T> type of result
 */
public interface FtHandlerTyped<T> {
    /**
     * Invoke this fault tolerance handler on a supplier of a result.
     * This method blocks until all FT handlers are resolved.
     *
     * @param supplier that provides the initial value for processing; depending on handler type, the supplier may be called
     *                 multiple times
     * @return a value with result after the fault tolerance operations
     * @throws java.lang.RuntimeException in case the underlying supplier threw an exception, or one of the FT handlers failed
     */
    T invoke(Supplier<? extends T> supplier);
}
