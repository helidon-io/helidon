/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.json.schema;

import java.util.Objects;

/**
 * Json schema related exception.
 */
public final class JsonSchemaException extends RuntimeException {
    /**
     * Create a new schema exception with the provided message.
     *
     * @param message descriptive error message
     * @throws java.lang.NullPointerException if the message is null
     */
    public JsonSchemaException(String message) {
        super(Objects.requireNonNull(message));
    }

    /**
     * Create a new schema exception with the provided message and a cause.
     *
     * @param message descriptive error message
     * @param cause   the cause of this exception
     * @throws java.lang.NullPointerException if either message or cause is null
     */
    public JsonSchemaException(String message, Throwable cause) {
        super(Objects.requireNonNull(message),
              Objects.requireNonNull(cause));
    }
}
