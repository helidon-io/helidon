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

package io.helidon.metadata.hjson;

import java.util.Objects;

/**
 * Exception marking a problem with JSON operations.
 */
public class JException extends RuntimeException {
    /**
     * Create a new instance with a customized message.
     *
     * @param message message to use
     */
    public JException(String message) {
        super(Objects.requireNonNull(message));
    }

    /**
     * Create a new instance with a customized message and cause.
     *
     * @param message message to use
     * @param cause cause of this exception
     */
    public JException(String message, Throwable cause) {
        super(Objects.requireNonNull(message),
              Objects.requireNonNull(cause));
    }
}
