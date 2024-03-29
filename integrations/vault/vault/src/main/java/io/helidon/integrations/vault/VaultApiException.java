/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

import java.util.Formatter;

import io.helidon.integrations.common.rest.ApiException;

/**
 * Exception in Vault communication not based on HTTP response.
 */
public class VaultApiException extends ApiException {

    /**
     * Vault exception with a descriptive message.
     *
     * @param message error message
     */
    public VaultApiException(String message) {
        super(message);
    }

    /**
     * Vault exception with a descriptive message.
     *
     * @param format a {@link Formatter} string
     * @param args   format string arguments
     */
    public VaultApiException(String format, Object... args) {
        super(String.format(format, args));
    }

    /**
     * Vault exception with message and a cause.
     *
     * @param message error message
     * @param cause   throwable that caused this exception
     */
    public VaultApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
