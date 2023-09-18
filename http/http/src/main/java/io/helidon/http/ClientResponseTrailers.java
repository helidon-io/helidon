/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * HTTP Trailer headers of a client response.
 */
public interface ClientResponseTrailers extends io.helidon.http.Headers {

    /**
     * Create new trailers from headers future.
     *
     * @param headers trailer headers
     * @return new client trailers from headers future
     */
    static ClientResponseTrailers create(io.helidon.http.Headers headers) {
        return new ClientResponseTrailersImpl(headers);
    }

    /**
     * Create new empty trailers.
     *
     * @return new empty client trailers
     */
    static ClientResponseTrailers create() {
        return new ClientResponseTrailersImpl();
    }
}
