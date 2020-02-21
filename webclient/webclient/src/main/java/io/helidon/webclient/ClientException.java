/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.util.Optional;

/**
 * An exception that caused this client request to fail. If the exception is based on status or content of response from
 * server, the {@link ClientResponse} is available to obtain any needed details.
 * If the exception was caused before data was sent, or due to timeouts, socket exceptions etc., the {@link ClientResponse} is
 * not present and exception handling must be based on the wrapped exception.
 */
public class ClientException extends RuntimeException {
    private ClientResponse response;

    /**
     * Creates new instance of web client exception.
     *
     * @param message exception message
     */
    public ClientException(String message) {
        super(message);
    }

    ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    ClientException(String message, Throwable cause, ClientResponse response) {
        super(message, cause);
        this.response = response;
    }

    /**
     * {@link ClientResponse} that caused this exception if caused by response from server. Otherwise an empty {@link Optional}.
     *
     * @return ClientResponse if this client request reached the moment when a response is received.
     */
    public Optional<ClientResponse> response() {
        return Optional.ofNullable(response);
    }
}
