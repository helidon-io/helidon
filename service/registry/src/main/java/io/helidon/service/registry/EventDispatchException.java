/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.Objects;

/**
 * This exception is thrown when event dispatching fails.
 */
public class EventDispatchException extends RuntimeException {
    /**
     * Create an exception with the first encountered exception as the cause.
     * Additional exceptions (if encountered) are added as {@link #getSuppressed()}.
     *
     * @param message descriptive message
     * @param cause cause of the failure
     */
    public EventDispatchException(String message, Throwable cause) {
        super(Objects.requireNonNull(message),
              Objects.requireNonNull(cause));
    }
}
