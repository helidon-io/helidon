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

package io.helidon.messaging;

import java.util.Objects;

import io.helidon.common.Api;

/**
 * Runtime exception thrown by the messaging runtime.
 */
@Api.Preview
public class MessagingException extends RuntimeException {
    /**
     * Create a messaging exception.
     *
     * @param message exception message
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public MessagingException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    /**
     * Create a messaging exception.
     *
     * @param message exception message
     * @param cause root cause
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     */
    public MessagingException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
