/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * An exception class that is a {@code RuntimeException} and is used to wrap
 * an exception that cannot be thrown in a supplier.
 */
public class SupplierException extends RuntimeException {

    /**
     * Create an instance using a {@code Throwable}.
     *
     * @param cause the cause
     */
    public SupplierException(Throwable cause) {
        super(cause);
    }

    /**
     * Create an instance using a {@code Throwable}.
     *
     * @param message the message
     * @param cause the cause
     */
    public SupplierException(String message, Throwable cause) {
        super(message, cause);
    }
}
