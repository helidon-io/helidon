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
package io.helidon.transaction;

import java.util.Objects;

/**
 * A {@link RuntimeException} that indicates that task with managed transaction has failed.
 */
public class TxException extends RuntimeException {

    /**
     * Create new exception for a message.
     *
     * @param message descriptive message, shall not be {@code null}
     * @throws NullPointerException when {@code message} is {@code null}
     */
    public TxException(String message) {
        super(Objects.requireNonNull(message, "Missing TxException message"));
    }

    /**
     * Create new exception for a message and a cause.
     *
     * @param message descriptive message, shall not be {@code null}
     * @param cause   original throwable causing this exception, shall not be {@code null}
     * @throws NullPointerException when {@code message} or {@code cause} is {@code null}
     */
    public TxException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "Missing TxException message"),
              Objects.requireNonNull(cause, "Missing TxException cause"));
    }

}
