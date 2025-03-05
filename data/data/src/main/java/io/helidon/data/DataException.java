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
package io.helidon.data;

import java.util.Objects;

/**
 * Helidon Data {@link RuntimeException}.
 */
public class DataException extends RuntimeException {

    /**
     * Create new exception for a message.
     *
     * @param message descriptive message
     */
    public DataException(String message) {
        super(checkMessage(message));
    }

    /**
     * Create new exception for a message and a cause.
     *
     * @param message descriptive message
     * @param cause original throwable causing this exception
     */
    public DataException(String message, Throwable cause) {
        super(checkMessage(message), checkCause(cause));
    }

    private static String checkMessage(String message) {
        Objects.requireNonNull(message, "Missing DataException message");
        return message;
    }

    private static Throwable checkCause(Throwable cause) {
        Objects.requireNonNull(cause, "Missing DataException cause");
        return cause;
    }

}
