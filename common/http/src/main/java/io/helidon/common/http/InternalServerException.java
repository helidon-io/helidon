/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.http;

/**
 * A runtime exception indicating a {@link io.helidon.common.http.Http.Status#INTERNAL_SERVER_ERROR_500 internal server error}.
 */
public class InternalServerException extends HttpException {
    /**
     * Creates {@link io.helidon.common.http.InternalServerException}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public InternalServerException(String message, Throwable cause) {
        super(message, Http.Status.INTERNAL_SERVER_ERROR_500, cause);
    }

    /**
     * Creates {@link io.helidon.common.http.InternalServerException}.
     *
     * @param message the message
     * @param cause the cause of this exception
     * @param keepAlive whether to keep the connection alive (if keep alives are enabled)
     */
    public InternalServerException(String message, Throwable cause, boolean keepAlive) {
        super(message, Http.Status.INTERNAL_SERVER_ERROR_500, cause, keepAlive);
    }
}
