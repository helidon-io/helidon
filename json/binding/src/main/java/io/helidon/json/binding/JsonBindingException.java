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

package io.helidon.json.binding;

import io.helidon.json.JsonException;

/**
 * Exception thrown during JSON binding operations.
 */
public class JsonBindingException extends JsonException {
    /**
     * Construct a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public JsonBindingException(String message) {
        super(message);
    }

    /**
     * Construct a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public JsonBindingException(String message, Exception cause) {
        super(message, cause);
    }
}
