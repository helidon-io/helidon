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

/**
 * A runtime exception indicating a {@link Status#NOT_FOUND_404 not found}.
 */
public class NotFoundException extends HttpException {

    /**
     * Creates {@link NotFoundException}.
     *
     * @param message the message
     */
    public NotFoundException(String message) {
        super(message, Status.NOT_FOUND_404, null, true);
    }

    /**
     * Creates {@link NotFoundException}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public NotFoundException(String message, Throwable cause) {
        super(message, Status.NOT_FOUND_404, cause, true);
    }
}
