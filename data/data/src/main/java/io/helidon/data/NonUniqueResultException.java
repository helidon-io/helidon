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

/**
 * Thrown when a query was expected to produce exactly one record but produced many instead.
 */
public class NonUniqueResultException extends DataException {

    /**
     * Creates a new {@link NonUniqueResultException} with the supplied detail message.
     *
     * @param message descriptive message, shall not be {@code null}
     * @throws NullPointerException when {@code message} is {@code null}
     */
    public NonUniqueResultException(String message) {
        super(message);
    }

    /**
     * Creates a new {@link NonUniqueResultException} with the supplied detail message and cause.
     *
     * @param message descriptive message, shall not be {@code null}
     * @param cause   original throwable causing this exception, shall not be {@code null}
     * @throws NullPointerException when {@code message} or {@code cause} is {@code null}
     */
    public NonUniqueResultException(String message, Throwable cause) {
        super(message, cause);
    }

}
