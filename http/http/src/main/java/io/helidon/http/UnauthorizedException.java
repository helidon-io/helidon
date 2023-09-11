/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import static io.helidon.http.Status.UNAUTHORIZED_401;

/**
 * A runtime exception indicating a {@link Status#UNAUTHORIZED_401 unauthorized}.
 */
public class UnauthorizedException extends HttpException {

    /**
     * Creates {@link UnauthorizedException}.
     *
     * @param message the message
     */
    public UnauthorizedException(String message) {
        super(message, UNAUTHORIZED_401, null, true);
    }

    /**
     * Creates {@link UnauthorizedException}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, UNAUTHORIZED_401, cause, true);
    }
}
