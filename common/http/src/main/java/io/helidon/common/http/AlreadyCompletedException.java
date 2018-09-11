/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

/**
 * Signals that a mutation method has been invoked on a resource that is already completed.
 *
 * It is no longer possible to mute state of these objects.
 */
public class AlreadyCompletedException extends IllegalStateException {

    /**
     * Constructs an {@link AlreadyCompletedException} with the specified detail message.
     *
     * @param s the String that contains a detailed message.
     */
    public AlreadyCompletedException(String s) {
        super(s);
    }

    /**
     * Constructs an {@link AlreadyCompletedException} with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method).
     *                (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AlreadyCompletedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an {@link AlreadyCompletedException} with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method).
     *              (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AlreadyCompletedException(Throwable cause) {
        super(cause);
    }
}
