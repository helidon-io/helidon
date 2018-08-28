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

/**
 * The routing error handler.
 * Can be mapped to the error cause in the {@link Routing}.
 *
 * @param <T> Type of handled error.
 * @see Routing.Builder
 * @see Routing.Rules
 */
@FunctionalInterface
public interface ErrorHandler<T extends Throwable> {

    /**
     * Error handling consumer.
     *
     * @param req the server request
     * @param res the server response
     * @param ex the cause of the error
     */
    void accept(ServerRequest req, ServerResponse res, T ex);
}
