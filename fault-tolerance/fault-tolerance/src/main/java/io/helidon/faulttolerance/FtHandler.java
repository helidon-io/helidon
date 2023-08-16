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
 * A fault tolerance handler.
 * The following handlers do NOT implement this interface:
 * <ul>
 *     <li>{@link Fallback} - implements {@link FtHandlerTyped} instead.</li>
 * </ul>
 */
public interface FtHandler {

    /**
     * A name assigned to a handler for debugging, error reporting or configuration purposes.
     *
     * @return a non-null name for this handler
     */
    String name();

    /**
     * Invoke this fault tolerance handler on a supplier of result.
     *
     * @param supplier that provides the initial value for processing; depending on handler type, the supplier may be called
     *                 multiple times
     * @param <T>      type of result
     * @return result obtained from the supplier after carrying out fault tolerance rules
     * @throws java.lang.RuntimeException one of the FT exceptions if the handler fails
     */
    <T> T invoke(Supplier<? extends T> supplier);
}
