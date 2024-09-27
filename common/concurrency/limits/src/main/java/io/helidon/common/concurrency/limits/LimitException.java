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
 * A limit was reached and the submitted task cannot be executed.
 *
 * @see io.helidon.common.concurrency.limits.Limit#invoke(java.util.concurrent.Callable)
 * @see io.helidon.common.concurrency.limits.Limit#invoke(Runnable)
 */
public class LimitException extends RuntimeException {
    /**
     * A new limit exception with a cause.
     *
     * @param cause cause of the limit reached
     */
    public LimitException(Exception cause) {
        super(Objects.requireNonNull(cause));
    }

    /**
     * A new limit exception with a message.
     *
     * @param message description of why the limit was reached
     */
    public LimitException(String message) {
        super(Objects.requireNonNull(message));
    }
}
