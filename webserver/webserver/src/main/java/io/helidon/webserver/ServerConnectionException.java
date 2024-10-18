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

package io.helidon.webserver;

import java.util.Objects;

/**
 * An exception that was caused by server communication error (on the original client call).
 * <p>
 * This exception must bubble up through the error handling chain, as otherwise we would treat it
 * as an internal exception, and may end up logging too much information.
 */
public class ServerConnectionException extends CloseConnectionException {
    /**
     * Server connection exception based on a cause.
     *
     * @param message descriptive message
     * @param cause   cause of this exception
     */
    public ServerConnectionException(String message, Throwable cause) {
        super(Objects.requireNonNull(message),
              Objects.requireNonNull(cause));
    }
}
