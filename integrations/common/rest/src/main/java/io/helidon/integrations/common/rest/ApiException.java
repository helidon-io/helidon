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

package io.helidon.integrations.common.rest;

/**
 * API exception that is not related to processing of a response.
 *
 * @see io.helidon.integrations.common.rest.RestException
 */
public class ApiException extends RuntimeException {
    /**
     * New exception without a cause and message.
     */
    public ApiException() {
    }

    /**
     * New exception with a message and without a cause.
     * @param message message to use
     */
    public ApiException(String message) {
        super(message);
    }

    /**
     * New exception with a cause and message.
     *
     * @param message message to use
     * @param cause throwable that caused this exception
     */
    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * New exception with a cause and without a message.
     *
     * @param cause throwable that caused this exception
     */
    public ApiException(Throwable cause) {
        super(cause);
    }
}
