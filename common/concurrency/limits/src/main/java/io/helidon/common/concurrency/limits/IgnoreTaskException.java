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

package io.helidon.common.concurrency.limits;

import java.util.Objects;

/**
 * If this exception is thrown from a limited task within
 * {@link Limit#invoke(java.util.concurrent.Callable)}, the
 * invocation will be ignored by possible algorithms (for example when considering round-trip timing).
 * <p>
 * This should be used for cases where we never got to execute the intended task.
 * This exception should never be thrown by {@link Limit}, it should always
 * be translated to a proper return type, or actual exception.
 */
public class IgnoreTaskException extends RuntimeException {
    /**
     * Desired return value, if we want to ignore the result, yet we still provide valid response.
     */
    private final Object returnValue;
    /**
     * Exception to throw to the user. This is to allow throwing an exception while ignoring it for limits algorithm.
     */
    private final Exception exception;

    /**
     * Create a new instance with a cause.
     *
     * @param cause the cause of this exception
     */
    public IgnoreTaskException(Exception cause) {
        super(Objects.requireNonNull(cause));

        this.exception = cause;
        this.returnValue = null;
    }

    /**
     * Create a new instance with a return value.
     *
     * @param returnValue value to return, even though this invocation should be ignored
     *                    return value may be {@code null}.
     */
    public IgnoreTaskException(Object returnValue) {
        this.exception = null;
        this.returnValue = returnValue;
    }

    /**
     * This is used by limit implementations to either return the value, or throw an exception.
     *
     * @return the value provided to be the return value
     * @param <T> type of the return value
     * @throws Exception exception provided by the task
     */
    @SuppressWarnings("unchecked")
    public <T> T handle() throws Exception {
        if (returnValue == null && exception != null) {
            throw exception;
        }
        return (T) returnValue;
    }
}
