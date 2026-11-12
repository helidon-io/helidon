/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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
 * <p>
 * Before invoking each matching handler, routing removes uncommitted entity headers:
 * {@code Content-Length}, {@code Transfer-Encoding}, {@code Trailer}, {@code Content-Range},
 * {@code Content-Type}, {@code Content-Encoding}, {@code Content-Language}, {@code Content-Location},
 * {@code Content-Disposition}, {@code Content-Digest}, {@code Content-MD5}, {@code Digest},
 * {@code Repr-Digest}, {@code ETag}, {@code Last-Modified}, and {@code Accept-Ranges}.
 * This also applies between handlers when forwarding with {@link ServerRequest#next()} or
 * {@link ServerRequest#next(Throwable)}. The handler sending a replacement entity must set its entity metadata.
 * The reset itself preserves the status and other headers, including cookies, authentication challenges,
 * and application headers. Committed headers are left unchanged; matching handlers are still invoked.
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
