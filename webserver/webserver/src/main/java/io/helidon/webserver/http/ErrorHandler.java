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

package io.helidon.webserver.http;

/**
 * The routing error handler.
 * Can be mapped to the error cause in {@link HttpRouting}.
 *
 * @param <T> type of throwable handled by this handler
 * @see io.helidon.webserver.http.HttpRouting.Builder#error(Class, ErrorHandler)
 */
@FunctionalInterface
public interface ErrorHandler<T extends Throwable> {
    /**
     * Error handling consumer.
     * Do not throw an exception from an error handler, it would make this error handler invalid and the exception would be
     * ignored.
     *
     * @param req the server request
     * @param res the server response
     * @param throwable the cause of the error
     */
    void handle(ServerRequest req, ServerResponse res, T throwable);
}
