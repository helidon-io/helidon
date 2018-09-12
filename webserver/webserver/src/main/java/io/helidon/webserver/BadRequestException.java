/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.http.Http;

/**
 * A runtime exception indicating a {@link Http.Status#BAD_REQUEST_400 bad request}.
 */
public class BadRequestException extends HttpException {

    /**
     * Creates {@link BadRequestException}.
     *
     * @param message the message
     */
    public BadRequestException(String message) {
        super(message, Http.Status.BAD_REQUEST_400);
    }

    /**
     * Creates {@link BadRequestException}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, Http.Status.BAD_REQUEST_400, cause);
    }
}
