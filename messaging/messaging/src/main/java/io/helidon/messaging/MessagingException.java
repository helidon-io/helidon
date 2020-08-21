/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging;

/**
 * Reactive Messaging specific exception.
 */
public class MessagingException extends RuntimeException {

    /**
     * Create new MessagingException with supplied message.
     *
     * @param message supplied message
     */
    public MessagingException(String message) {
        super(message);
    }

    /**
     * Create new MessagingException with supplied message and cause.
     *
     * @param message supplied message
     * @param cause   of this exception
     */
    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
