/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

/**
 * General timeout runtime exception.
 */
public class TimeoutException extends FaultToleranceException {
    private static final long serialVersionUID = 1900924677490550714L;

    /**
     * A new timeout exception with custom message.
     *
     * @param message detail message
     */
    public TimeoutException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code TimeoutException} with the specified detail
     * message.
     *
     * @param message   the detail message
     * @param throwable last retry exception
     */
    public TimeoutException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

