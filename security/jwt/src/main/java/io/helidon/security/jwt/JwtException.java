/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

/**
 * A RuntimeException for the JWT and JWK world.
 */
public class JwtException extends RuntimeException {
    /**
     * Exception with a message.
     *
     * @param message message to propagate
     */
    public JwtException(String message) {
        super(message);
    }

    /**
     * Exception with a message and a cause.
     *
     * @param message message to propagate
     * @param cause   cause of this exception
     */
    public JwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
