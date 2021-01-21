/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeoutException;

/**
 * Subclass of {@link TimeoutException} to discern exceptions thrown by a {@link Retry}
 * when its overall timeout is reached versus those thrown by a {@link Timeout}.
 */
public class RetryTimeoutException extends TimeoutException {
    private static final long serialVersionUID = 1900926677490550714L;

    private final Throwable lastRetryException;

    /**
     * Constructs a {@code RetryTimeoutException} with the specified detail
     * message.
     *
     * @param throwable last retry exception
     * @param message the detail message
     */
    public RetryTimeoutException(Throwable throwable, String message) {
        super(message);
        lastRetryException = throwable;
    }

    /**
     * Last exception thrown in {@code Retry} before the overall timeout reached.
     *
     * @return last exception thrown
     */
    public Throwable lastRetryException() {
        return lastRetryException;
    }
}

